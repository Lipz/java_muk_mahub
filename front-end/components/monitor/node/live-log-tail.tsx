"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useDeferredValue, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { LogChannel, LogStreamBackfill, LogStreamLine } from "@/src/lib/types";
import { compileFilter, highlightSegments, type FilterOptions, type Matcher } from "./log-filter";

/**
 * Lines kept in memory. More than are shown, so a filter can still find a rare
 * line that a thousand noisy ones have already pushed off the screen.
 */
const BUFFER_LINES = 5000;
/** Rows rendered at once, filtered or not; older ones fall off the top. */
const SHOWN_LINES = 1000;
/** The hub pings every 15s: three missed pings means the stream is dead. */
const STALL_MS = 45_000;
const BACKOFF_MS = [1_000, 2_000, 5_000, 10_000, 30_000];
/** Below the log: the tab's bottom padding, plus a little air. */
const BELOW_PANEL_PX = 30;
const MIN_HEIGHT_PX = 240;

type Status = "connecting" | "live" | "reconnecting" | "evicted" | "signed-out" | "forbidden" | "missing";

type Row = { key: number; kind: "line" | "marker"; text: string };

type SseEvent = { event: string; data: string };

const dim = "text-[color-mix(in_srgb,#f2f2f3_40%,transparent)]";

/**
 * Split a text/event-stream body into events. Comments (the hub's pings) are
 * not events, but any bytes at all count as `onActivity`.
 */
async function* readSse(body: ReadableStream<Uint8Array>, onActivity: () => void): AsyncGenerator<SseEvent> {
  const reader = body.getReader();
  // stream: true keeps a multi-byte character split across chunks intact
  const decoder = new TextDecoder();
  let buf = "";
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) return;
      onActivity();
      buf += decoder.decode(value, { stream: true });
      // A chunk can end between the \r and \n of one line break: hold that \r back
      const heldCr = buf.endsWith("\r");
      buf = (heldCr ? buf.slice(0, -1) : buf).replace(/\r\n?/g, "\n");
      let cut: number;
      while ((cut = buf.indexOf("\n\n")) >= 0) {
        const block = buf.slice(0, cut);
        buf = buf.slice(cut + 2);
        let event = "message";
        const data: string[] = [];
        for (const line of block.split("\n")) {
          if (line.startsWith(":")) continue;
          const colon = line.indexOf(":");
          const field = colon < 0 ? line : line.slice(0, colon);
          const val = colon < 0 ? "" : line.slice(colon + 1).replace(/^ /, "");
          if (field === "event") event = val;
          else if (field === "data") data.push(val);
        }
        if (data.length) yield { event, data: data.join("\n") };
      }
      if (heldCr) buf += "\r";
    }
  } finally {
    reader.releaseLock();
  }
}

/**
 * Live tail of one log channel: today's last lines, then every new line as it
 * is published. Reads the stream with fetch rather than EventSource so it can
 * tell a lost session (401) from a hub restart (502) and pace its own retries.
 * Mount it with `key={channel.uuid}` so switching file starts a fresh stream.
 */
export function LiveLogTail({ channel }: { channel: LogChannel }) {
  const pathname = usePathname();
  const [rows, setRows] = useState<Row[]>([]);
  const [status, setStatus] = useState<Status>("connecting");
  const [follow, setFollow] = useState(true);
  const [unseen, setUnseen] = useState(0);
  // Whether today's file has anything to download: known from the backfill and live lines
  const [hasToday, setHasToday] = useState(false);
  // Bumped by "Resume" to open the stream again after an eviction
  const [session, setSession] = useState(0);
  const scroller = useRef<HTMLDivElement>(null);
  // Read by the stream callbacks, which must not restart when it changes
  const followRef = useRef(true);

  // grep box; typing stays responsive while 5000 lines are re-filtered behind it
  const [findOpen, setFindOpen] = useState(false);
  const findInput = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState("");
  const [opts, setOpts] = useState<FilterOptions>({ caseSensitive: false, regex: false, invert: false });
  const deferredQuery = useDeferredValue(query);
  const compiled = useMemo(() => compileFilter(deferredQuery, opts), [deferredQuery, opts]);
  // An invalid regex filters nothing until it is fixed
  const matcher: Matcher | null = compiled === "invalid" ? null : compiled;
  const matcherRef = useRef<Matcher | null>(null);
  useEffect(() => {
    matcherRef.current = matcher;
  }, [matcher]);

  useEffect(() => {
    const url = `/api/logs/${encodeURIComponent(channel.uuid)}/stream`;
    const closed = new AbortController();
    let nextKey = 0;
    let lastSeq = -1;
    let retry = 0;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    // Lines can arrive hundreds a second: batch them into one render per frame
    let pending: Row[] = [];
    let replace = false;
    let frame = 0;
    const flush = () => {
      frame = 0;
      const batch = pending;
      const wasReplace = replace;
      pending = [];
      replace = false;
      setRows((prev) => {
        const next = wasReplace ? batch : prev.concat(batch);
        return next.length > BUFFER_LINES ? next.slice(-BUFFER_LINES) : next;
      });
      if (!followRef.current) {
        // Only lines the filter lets through are news to the reader
        const m = matcherRef.current;
        const added = batch.filter((r) => r.kind === "line" && (!m || m.test(r.text))).length;
        setUnseen((n) => (wasReplace ? 0 : n + added));
      }
    };
    const push = (items: Row[], replaceAll = false) => {
      if (replaceAll) {
        pending = items;
        replace = true;
      } else {
        pending.push(...items);
      }
      // Frames do not run in a background tab: keep only what could be shown
      if (pending.length > BUFFER_LINES) pending = pending.slice(-BUFFER_LINES);
      if (!frame) frame = requestAnimationFrame(flush);
    };
    const line = (text: string): Row => ({ key: nextKey++, kind: "line", text });
    const marker = (text: string): Row => ({ key: nextKey++, kind: "marker", text });

    const connect = async () => {
      const attempt = new AbortController();
      const stop = () => attempt.abort();
      closed.signal.addEventListener("abort", stop);
      let stallTimer: ReturnType<typeof setTimeout> | undefined;
      const armStall = () => {
        clearTimeout(stallTimer);
        stallTimer = setTimeout(stop, STALL_MS);
      };

      try {
        armStall();
        const res = await fetch(url, { signal: attempt.signal, cache: "no-store" });
        // These will not fix themselves by retrying
        if (res.status === 401) return setStatus("signed-out");
        if (res.status === 403) return setStatus("forbidden");
        if (res.status === 404) return setStatus("missing");
        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`);

        for await (const ev of readSse(res.body, armStall)) {
          if (ev.event === "backfill") {
            const b = JSON.parse(ev.data) as LogStreamBackfill;
            lastSeq = b.seq;
            retry = 0;
            setStatus("live");
            setHasToday(b.lines.length > 0);
            push(
              [...b.lines.map(line), marker(b.lines.length ? "live from here" : "nothing written today — waiting for lines")],
              true,
            );
          } else if (ev.event === "line") {
            const l = JSON.parse(ev.data) as LogStreamLine;
            const items: Row[] = [];
            if (lastSeq >= 0 && l.seq > lastSeq + 1) {
              const missed = l.seq - lastSeq - 1;
              items.push(marker(`${missed} line${missed === 1 ? "" : "s"} missed`));
            }
            lastSeq = l.seq;
            setHasToday(true);
            items.push(line(l.line));
            push(items);
          } else if (ev.event === "evicted") {
            // Opened in too many other tabs; reconnecting would just evict one of those
            return setStatus("evicted");
          }
        }
        // Stream ended without an event: hub restarted or dropped a slow reader
      } catch {
        // Network error or stall: retry below, unless we are unmounting
      } finally {
        clearTimeout(stallTimer);
        closed.signal.removeEventListener("abort", stop);
        attempt.abort();
      }

      if (closed.signal.aborted) return;
      setStatus("reconnecting");
      retryTimer = setTimeout(connect, BACKOFF_MS[Math.min(retry++, BACKOFF_MS.length - 1)]);
    };

    connect();
    return () => {
      closed.abort();
      clearTimeout(retryTimer);
      cancelAnimationFrame(frame);
    };
  }, [channel.uuid, session]);

  // Fill the window below the panel's top edge, so at the top of the page the
  // tail reaches the bottom of the screen without scrolling the page itself.
  // Measured rather than calc(100vh - N): the header above wraps with width.
  useLayoutEffect(() => {
    const el = scroller.current;
    if (!el) return;
    const fit = () => {
      const top = el.getBoundingClientRect().top + window.scrollY;
      const height = Math.max(MIN_HEIGHT_PX, window.innerHeight - top - BELOW_PANEL_PX);
      el.style.height = `${height}px`;
      if (followRef.current) el.scrollTop = el.scrollHeight;
    };
    fit();
    window.addEventListener("resize", fit);
    return () => window.removeEventListener("resize", fit);
  }, []);

  // Markers (live from here, lines missed) always show: they explain the stream
  const visible = useMemo(() => {
    const kept = matcher ? rows.filter((r) => r.kind === "marker" || matcher.test(r.text)) : rows;
    return kept.length > SHOWN_LINES ? kept.slice(-SHOWN_LINES) : kept;
  }, [rows, matcher]);
  const lineCount = useMemo(() => rows.filter((r) => r.kind === "line").length, [rows]);
  const matchCount = useMemo(
    () => (matcher ? rows.filter((r) => r.kind === "line" && matcher.test(r.text)).length : lineCount),
    [rows, matcher, lineCount],
  );

  // Stick to the bottom while following, also when the filter changes what is shown
  useLayoutEffect(() => {
    const el = scroller.current;
    if (el && followRef.current) el.scrollTop = el.scrollHeight;
  }, [visible]);

  const onScroll = () => {
    const el = scroller.current;
    if (!el) return;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 24;
    if (atBottom === followRef.current) return;
    followRef.current = atBottom;
    setFollow(atBottom);
    if (atBottom) setUnseen(0);
  };

  const jumpToLive = () => {
    const el = scroller.current;
    if (el) el.scrollTop = el.scrollHeight;
  };

  const resume = () => {
    setStatus("connecting");
    setSession((n) => n + 1);
  };

  const openFind = () => {
    setFindOpen(true);
    // Already open: just bring the caret back to the box
    findInput.current?.focus();
    findInput.current?.select();
  };

  // Closing the find widget drops the filter, like a browser's find bar: a
  // filter nobody can see would silently hide lines
  const closeFind = () => {
    setFindOpen(false);
    setQuery("");
    scroller.current?.focus();
  };

  // ⌘F / Ctrl+F inside the panel, or "/" on the log, open the find widget
  // instead of the browser's own find, which cannot see filtered lines anyway
  const onPanelKeyDown = (e: React.KeyboardEvent) => {
    const typing = e.target instanceof HTMLInputElement;
    if ((e.key === "f" && (e.metaKey || e.ctrlKey)) || (e.key === "/" && !typing)) {
      e.preventDefault();
      openFind();
    }
  };

  const from = `${pathname}?tab=log&file=${encodeURIComponent(channel.uuid)}`;
  const filtering = matcher != null;

  return (
    <div onKeyDown={onPanelKeyDown}>
      {/* Pinned toolbar: stays put while the log scrolls under it */}
      <div className="mono flex items-center justify-between gap-3 border-b border-[color-mix(in_srgb,#f2f2f3_10%,transparent)] bg-[color-mix(in_srgb,#000_22%,transparent)] px-4 py-2 text-[11px]">
        <span className="min-w-0 truncate" title={channel.pubPath}>
          <span className="text-[12.5px] font-semibold text-[#f2f2f3]">{channel.channel}</span>
          <span className={`ml-2 ${dim}`}>{channel.pubPath}</span>
        </span>
        <span className="flex flex-none items-center gap-2">
          <button
            type="button"
            onClick={findOpen ? closeFind : openFind}
            aria-pressed={findOpen}
            aria-keyshortcuts="Meta+F Control+F /"
            title={findOpen ? "Close filter (Esc)" : "Filter lines (⌘F, /)"}
            className={`${toolButton} cursor-pointer ${
              findOpen || filtering
                ? "border-[color-mix(in_srgb,#f2f2f3_55%,transparent)] bg-accent-700 text-[#f2f2f3]"
                : "border-[color-mix(in_srgb,#f2f2f3_22%,transparent)] text-[color-mix(in_srgb,#f2f2f3_70%,transparent)] hover:text-[#f2f2f3]"
            }`}
          >
            <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" className="size-3 flex-none" aria-hidden>
              <circle cx="7" cy="7" r="4.25" />
              <path d="m10.25 10.25 3.25 3.25" strokeLinecap="round" />
            </svg>
            grep
          </button>
          <DownloadToday logId={channel.uuid} channel={channel.channel} enabled={hasToday} />
          <StatusBadge status={status} onResume={resume} signInHref={`/login?from=${encodeURIComponent(from)}`} />
        </span>
      </div>

      <div className="relative">
        <div
          ref={scroller}
          onScroll={onScroll}
          role="log"
          aria-label={`Live tail of ${channel.channel}`}
          tabIndex={0}
          className="logscroll mono px-4 py-3 text-[12px] leading-[1.75] text-[color-mix(in_srgb,#f2f2f3_78%,transparent)] focus:outline-none"
        >
          <div className={dim}># saved   {channel.savePath}/&lt;yyyy-MM-dd&gt;.log</div>

          <div className="mt-3">
            {visible.map((r) =>
              r.kind === "line" ? (
                <div key={r.key} className="break-words whitespace-pre-wrap">
                  {matcher?.highlight
                    ? highlightSegments(r.text, matcher.highlight).map((seg, i) =>
                        seg.match ? (
                          <mark key={i} className="rounded-[3px] bg-[#e0b25f] text-accent-900">
                            {seg.text}
                          </mark>
                        ) : (
                          seg.text
                        ),
                      )
                    : r.text}
                </div>
              ) : (
                <div
                  key={r.key}
                  className="my-2 flex items-center gap-2.5 text-[10px] tracking-[.08em] text-[color-mix(in_srgb,#f2f2f3_50%,transparent)] uppercase before:h-px before:flex-1 before:bg-[color-mix(in_srgb,#f2f2f3_12%,transparent)] after:h-px after:flex-1 after:bg-[color-mix(in_srgb,#f2f2f3_12%,transparent)]"
                >
                  <span className="rounded-full bg-[color-mix(in_srgb,#f2f2f3_8%,transparent)] px-2.5 py-[2px]">{r.text}</span>
                </div>
              ),
            )}
            {status === "connecting" && rows.length === 0 ? <div className={dim}>Connecting…</div> : null}
            {filtering && lineCount > 0 && matchCount === 0 ? (
              <div className={dim}>No line matches — new lines are still checked as they arrive.</div>
            ) : null}
          </div>
        </div>

        {findOpen ? (
          <FindOverlay
            inputRef={findInput}
            query={query}
            onQuery={setQuery}
            opts={opts}
            onOpts={setOpts}
            invalid={compiled === "invalid"}
            matchCount={matchCount}
            lineCount={lineCount}
            onClose={closeFind}
          />
        ) : null}

        {!follow ? (
          <button
            type="button"
            onClick={jumpToLive}
            className="mono absolute right-4 bottom-3 cursor-pointer rounded-full border border-[color-mix(in_srgb,#f2f2f3_25%,transparent)] bg-accent-700 px-3 py-1 text-[11px] text-[#f2f2f3] shadow-[0_4px_14px_rgba(0,0,0,.4)] hover:bg-accent-600"
          >
            ↓ {unseen > 0 ? `${unseen} new line${unseen === 1 ? "" : "s"}` : "jump to live"}
          </button>
        ) : null}
      </div>
    </div>
  );
}

const toolButton =
  "inline-flex h-6 flex-none items-center gap-1.5 rounded-md border px-2.5 text-[11px] leading-none whitespace-nowrap";

/**
 * Find widget floating over the top-right of the log, so opening it moves no
 * line. Plain text by default, case-insensitive, with toggles for case, regex
 * and invert. Esc or × closes it and drops the filter.
 */
function FindOverlay({
  inputRef,
  query,
  onQuery,
  opts,
  onOpts,
  invalid,
  matchCount,
  lineCount,
  onClose,
}: {
  inputRef: React.RefObject<HTMLInputElement | null>;
  query: string;
  onQuery: (q: string) => void;
  opts: FilterOptions;
  onOpts: (o: FilterOptions) => void;
  invalid: boolean;
  matchCount: number;
  lineCount: number;
  onClose: () => void;
}) {
  const toggles: { key: keyof FilterOptions; label: string; title: string }[] = [
    { key: "caseSensitive", label: "Aa", title: "Match case" },
    { key: "regex", label: ".*", title: "Regular expression" },
    { key: "invert", label: "-v", title: "Show lines that do not match" },
  ];

  return (
    <div
      role="search"
      className={`mono absolute top-2.5 right-4 z-10 flex w-[min(440px,calc(100%-32px))] items-center gap-1 rounded-lg border bg-accent-800 p-1.5 pl-2.5 text-[11px] shadow-[0_8px_28px_rgba(0,0,0,.5)] ${
        invalid ? "border-[#ef8a80]" : "border-[color-mix(in_srgb,#f2f2f3_30%,transparent)]"
      }`}
    >
      <input
        ref={inputRef}
        autoFocus
        type="search"
        value={query}
        onChange={(e) => onQuery(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.preventDefault();
            onClose();
          }
        }}
        placeholder={opts.regex ? "grep pattern" : "grep text"}
        aria-label="Filter log lines"
        aria-invalid={invalid || undefined}
        spellCheck={false}
        className="h-[22px] min-w-0 flex-1 border-0 bg-transparent px-1 py-0 text-[12px] text-[#f2f2f3] shadow-none outline-none placeholder:text-[color-mix(in_srgb,#f2f2f3_35%,transparent)] focus-visible:outline-none [&::-webkit-search-cancel-button]:hidden"
      />
      <span aria-live="polite" className={`flex-none whitespace-nowrap ${invalid ? "text-[#ef8a80]" : dim}`}>
        {invalid ? "invalid regex" : query ? `${matchCount} / ${lineCount}` : ""}
      </span>
      {toggles.map((t) => {
        const on = opts[t.key];
        return (
          <button
            key={t.key}
            type="button"
            title={t.title}
            aria-pressed={on}
            onClick={() => onOpts({ ...opts, [t.key]: !on })}
            className={`h-6 min-w-[26px] flex-none cursor-pointer rounded-md border px-1 ${
              on
                ? "border-[color-mix(in_srgb,#f2f2f3_55%,transparent)] bg-accent-600 text-[#f2f2f3]"
                : "border-transparent text-[color-mix(in_srgb,#f2f2f3_55%,transparent)] hover:text-[#f2f2f3]"
            }`}
          >
            {t.label}
          </button>
        );
      })}
      <button
        type="button"
        onClick={onClose}
        title="Close (Esc)"
        aria-label="Close filter"
        className="h-6 w-6 flex-none cursor-pointer rounded-md text-[14px] leading-none text-[color-mix(in_srgb,#f2f2f3_55%,transparent)] hover:bg-[color-mix(in_srgb,#f2f2f3_10%,transparent)] hover:text-[#f2f2f3]"
      >
        ×
      </button>
    </div>
  );
}

/**
 * Today's file of the channel. A plain link styled as a button, so the browser's
 * own download manager streams it. Disabled until the tail has seen a line of
 * today: there is no file to fetch before.
 */
function DownloadToday({ logId, channel, enabled }: { logId: string; channel: string; enabled: boolean }) {
  const content = (
    <>
      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" className="size-3 flex-none" aria-hidden>
        <path d="M8 2v8M4.5 6.5 8 10l3.5-3.5M3 13h10" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      download
    </>
  );
  const base = `${toolButton} no-underline`;

  if (!enabled) {
    return (
      <span
        aria-disabled="true"
        title="Nothing written today yet"
        className={`${base} cursor-not-allowed border-[color-mix(in_srgb,#f2f2f3_18%,transparent)] text-[color-mix(in_srgb,#f2f2f3_35%,transparent)]`}
      >
        {content}
      </span>
    );
  }
  return (
    <a
      href={`/api/logs/${encodeURIComponent(logId)}/download`}
      download
      title={`Download today's ${channel} log`}
      className={`${base} border-[color-mix(in_srgb,#f2f2f3_40%,transparent)] cursor-pointer bg-accent-800 text-[#f2f2f3] hover:bg-accent-700`}
    >
      {content}
    </a>
  );
}

function StatusBadge({ status, onResume, signInHref }: { status: Status; onResume: () => void; signInHref: string }) {
  // Tinted pill in the status colour; `action` adds a link or button after the text
  const pill = (color: string, text: string, opts: { pulse?: boolean; action?: React.ReactNode } = {}) => (
    <span
      className="inline-flex h-6 flex-none items-center gap-1.5 rounded-full px-2.5 whitespace-nowrap"
      style={{ color, background: `color-mix(in srgb, ${color} 14%, transparent)` }}
    >
      <span className={`size-[6px] flex-none rounded-full ${opts.pulse ? "animate-pulse" : ""}`} style={{ background: color }} />
      {text}
      {opts.action ? <span className="text-[color-mix(in_srgb,#f2f2f3_40%,transparent)]">·</span> : null}
      {opts.action}
    </span>
  );
  const actionClass = "cursor-pointer text-[#f2f2f3] underline decoration-[color-mix(in_srgb,#f2f2f3_40%,transparent)] underline-offset-2 hover:decoration-[#f2f2f3]";

  return (
    <span role="status" aria-live="polite" className="text-[11px]">
      {status === "connecting" && pill("#b9bcc8", "connecting", { pulse: true })}
      {status === "live" && pill("#7fd6a0", "live")}
      {status === "reconnecting" && pill("#e0b25f", "reconnecting", { pulse: true })}
      {status === "forbidden" && pill("#ef8a80", "not allowed")}
      {status === "missing" && pill("#ef8a80", "channel no longer exists")}
      {status === "signed-out" &&
        pill("#ef8a80", "session expired", {
          action: (
            <Link href={signInHref} className={actionClass}>
              sign in
            </Link>
          ),
        })}
      {status === "evicted" &&
        pill("#e0b25f", "paused, open in another tab", {
          action: (
            <button type="button" onClick={onResume} className={actionClass}>
              resume here
            </button>
          ),
        })}
    </span>
  );
}
