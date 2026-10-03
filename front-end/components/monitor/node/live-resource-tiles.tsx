"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Fragment, useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import { formatBytes, formatPct, formatRate } from "@/src/lib/format";
import { readSse } from "@/src/lib/sse";
import type { ResourcePoint, ResourceSnapshot, ResourceStreamBackfill } from "@/src/lib/types";
import { Tile, toneVar, type Spark } from "./shared";

/**
 * CPU, memory and network tiles that follow the node live: the last minute
 * from the hub's resource stream, then every scrape as it lands.
 * Shared by the App/Web and DB templates; each passes its own disk tile and
 * the order the template puts the four in.
 */

/** Span the sparklines cover, and the trend badge compares across. */
export const LIVE_WINDOW_MINUTES = 1;
const WINDOW_MS = LIVE_WINDOW_MINUTES * 60_000;
/** No scrape for this long reads as stale — the hub's presence TTL. */
const STALE_MS = 15_000;
/** The hub pings every 15s: three missed pings means the stream is dead. */
const STALL_MS = 45_000;
const BACKOFF_MS = [1_000, 2_000, 5_000, 10_000, 30_000];
/** The trend badge compares the average of this much at each end of the window. */
const TREND_EDGE_MS = 10_000;
/** Change below which a metric reads "stable". */
const TREND_STEP = 5;
/** Narrowest scale of a percentage sparkline, in points. */
const MIN_PCT_SPAN = 4;

type Status = "connecting" | "live" | "reconnecting" | "paused" | "evicted" | "signed-out" | "missing";

/**
 * Answers that retrying or refocusing the tab will not fix. Only "Resume"
 * leaves them, so a tab coming back into view cannot evict another one.
 */
const isHalted = (s: Status) => s === "evicted" || s === "signed-out" || s === "missing";

type Sample = { at: number; cpu: number | null; mem: number | null; rx: number | null; tx: number | null };

/** Whether the tab is showing. The server renders as if it were. */
function usePageVisible(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      document.addEventListener("visibilitychange", onChange);
      return () => document.removeEventListener("visibilitychange", onChange);
    },
    () => document.visibilityState === "visible",
    () => true,
  );
}

export type ResourceTileId = "cpu" | "memory" | "disk" | "network";

const fromSnapshot = (r: ResourceSnapshot): Sample | null =>
  r.recordedAt == null
    ? null
    : { at: new Date(r.recordedAt).getTime(), cpu: r.cpuPct, mem: r.memUsedPct, rx: r.netRxBps, tx: r.netTxBps };

const fromPoint = (p: ResourcePoint): Sample => ({
  at: new Date(p.t).getTime(),
  cpu: p.cpuPct,
  mem: p.memPct,
  rx: p.netRxBps,
  tx: p.netTxBps,
});

export function LiveResourceTiles({
  uuid,
  initial,
  lastSeenAt,
  history,
  order,
  disk,
}: {
  uuid: string;
  /** Latest scrape at page load, shown until the stream answers. */
  initial: ResourceSnapshot | null;
  /** When the hub received that scrape (its clock), or null if unknown or not reporting. */
  lastSeenAt: string | null;
  /** Raw scrapes at page load (the same the stream backfills), drawn until the stream answers; only the last minute shows. */
  history: { points: ResourcePoint[]; to: number } | null;
  order: readonly ResourceTileId[];
  disk: ReactNode;
}) {
  const pathname = usePathname();
  const [latest, setLatest] = useState(initial);
  const [samples, setSamples] = useState<Sample[]>(() => (history?.points ?? []).map(fromPoint));
  // Starts at the server's clock so the first client render matches the server's
  const [now, setNow] = useState(() => history?.to ?? (initial ? fromSnapshot(initial)?.at ?? 0 : 0));
  // Browser clock minus agent clock. The window and staleness are measured on the
  // agent's clock, so an agent whose clock runs behind still lines up (seen in
  // practice: ~100s). Until the stream says, the page-load scrape gives it:
  // received (hub) minus stamped (agent).
  const [skew, setSkew] = useState(() => {
    const at = initial ? fromSnapshot(initial)?.at : undefined;
    return at != null && lastSeenAt ? new Date(lastSeenAt).getTime() - at : 0;
  });
  const [status, setStatus] = useState<Status>("connecting");
  const visible = usePageVisible();
  // Leaving a halted status ("Resume") re-runs the stream effect
  const halted = isHalted(status);

  // Keeps "stale" honest when nothing arrives
  useEffect(() => {
    if (!visible) return;
    const timer = setInterval(() => setNow(Date.now()), 1_000);
    return () => clearInterval(timer);
  }, [visible]);

  useEffect(() => {
    // A hidden tab gives its connection back; the backfill on return fills the gap
    if (halted || !visible) return;
    const url = `/api/servers/${encodeURIComponent(uuid)}/resources/stream`;
    const closed = new AbortController();
    let retry = 0;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    // Every measurement of the offset is the true offset plus some delay (delivery,
    // whole-second agent timestamps), never less: the smallest seen is the best, and
    // keeping it stops the line jumping sideways by a second or two on each scrape.
    // Per connection, so an agent whose clock was corrected is picked up on reconnect.
    let best = Infinity;
    const measured = (ms: number) => {
      if (ms < best) {
        best = ms;
        setSkew(ms);
      }
    };

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
        if (res.status === 404) return setStatus("missing");
        if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`);

        for await (const ev of readSse(res.body, armStall)) {
          if (ev.event === "backfill") {
            const b = JSON.parse(ev.data) as ResourceStreamBackfill;
            retry = 0;
            setStatus("live");
            // Assumes browser and hub clocks agree, which NTP'd machines do to well under a scrape
            if (b.skewMs != null) measured(b.skewMs);
            setSamples(b.samples.flatMap((r) => fromSnapshot(r) ?? []));
            if (b.samples.length) setLatest(b.samples[b.samples.length - 1]);
          } else if (ev.event === "sample") {
            const snap = JSON.parse(ev.data) as ResourceSnapshot;
            const s = fromSnapshot(snap);
            setLatest(snap);
            if (!s) continue;
            measured(Date.now() - s.at);
            setSamples((prev) => {
              // Agents can resend; the hub dedupes the backfill, not redeliveries
              if (prev.length && s.at <= prev[prev.length - 1].at) return prev;
              const horizon = s.at - WINDOW_MS;
              const kept = prev[0] && prev[0].at < horizon ? prev.filter((p) => p.at >= horizon) : prev;
              return [...kept, s];
            });
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
      // Whatever reopens it starts from scratch, unless it must not reopen at all
      setStatus((s) => (isHalted(s) ? s : "connecting"));
    };
  }, [uuid, visible, halted]);

  // Everything below is on the agent's clock.
  const latestAt = latest ? fromSnapshot(latest)?.at ?? null : null;
  // The window ends at the newest scrape, not at "now": the line steps when a scrape
  // lands and holds still in between, like the template's. Tied to the clock, the
  // server-rendered frame would be out of date by the time the browser showed it,
  // and the line would jump on every reload as the page caught up.
  const to = latestAt ?? now - skew;
  const from = to - WINDOW_MS;
  const shown = samples.filter((s) => s.at >= from);
  // Silence shows here instead, and as a break in the line once scrapes resume
  const stale = latestAt != null && now - skew - latestAt > STALE_MS;

  // One point per scrape: a minute at a 3-5s scrape is 12-20 points, too few to average further
  const series = (pick: (s: Sample) => number | null) =>
    shown.flatMap((s) => {
      const v = pick(s);
      return v == null ? [] : [{ at: s.at, v }];
    });
  const spark = (points: Spark["points"], max: number, min = 0): Spark => ({ points, from, to, min, max, gapMs: STALE_MS });
  // A percentage scaled to the minute's own range, like the template's lines: an idle
  // host at 2-3% would otherwise be a flat line along the bottom edge
  const pctSpark = (points: Spark["points"]) => {
    const { lo, hi } = fitRange(points);
    return spark(points, hi, lo);
  };

  const cpuPts = series((s) => s.cpu);
  const memPts = series((s) => s.mem);
  const netPts = series((s) => (s.rx == null && s.tx == null ? null : (s.rx ?? 0) + (s.tx ?? 0)));
  const netPeak = netPts.reduce((m, p) => Math.max(m, p.v), 0);
  const cpuTrend = trend(cpuPts);
  const memTrend = trend(memPts);

  const r = latest;
  const load = [r?.load1, r?.load5, r?.load15].map((l) => (l == null ? "—" : l.toFixed(2))).join(" / ");

  const tiles: Record<ResourceTileId, ReactNode> = {
    cpu: (
      <Tile
        key="cpu"
        label="CPU"
        value={formatPct(r?.cpuPct)}
        delta={stale ? "stale" : cpuTrend.label}
        deltaTone={stale || cpuTrend.up ? toneVar.crit : undefined}
        spark={pctSpark(cpuPts)}
        pct={r?.cpuPct}
        foot={r ? `${r.cpuCount ?? "—"} vCPU · load ${load}` : "no resource scrape"}
      />
    ),
    memory: (
      <Tile
        key="memory"
        label="Memory"
        value={formatPct(r?.memUsedPct)}
        delta={stale ? "stale" : memTrend.label}
        deltaTone={stale || memTrend.up ? toneVar.crit : undefined}
        spark={pctSpark(memPts)}
        pct={r?.memUsedPct}
        foot={
          r
            ? `${formatBytes(r.memUsedBytes)} / ${formatBytes(r.memTotalBytes)} · swap ${formatBytes(r.swapUsedBytes)}${
                r.swapUsedPct != null ? ` (${r.swapUsedPct.toFixed(0)}%)` : ""
              }${r.memEstimated ? " · est." : ""}`
            : "no resource scrape"
        }
      />
    ),
    disk: <Fragment key="disk">{disk}</Fragment>,
    network: (
      <Tile
        key="network"
        label="Network"
        value={r && (r.netRxBps != null || r.netTxBps != null) ? formatRate((r.netRxBps ?? 0) + (r.netTxBps ?? 0)) : "—"}
        delta={stale ? "stale" : "rx + tx"}
        deltaTone={stale ? toneVar.crit : undefined}
        // Rates have no natural ceiling: scale to the window's peak, with headroom
        spark={spark(netPts, netPeak * 1.15)}
        foot={r ? `rx ${formatRate(r.netRxBps)} · tx ${formatRate(r.netTxBps)}` : "no resource scrape"}
      />
    ),
  };

  return (
    <div>
      <div className="grid grid-cols-2 gap-5 px-6 pt-[22px] pb-1.5 lg:grid-cols-4">{order.map((id) => tiles[id])}</div>
      <StreamStatus
        status={!visible && !halted ? "paused" : status}
        onResume={() => setStatus("connecting")}
        signInHref={`/login?from=${encodeURIComponent(pathname)}`}
      />
    </div>
  );
}

/**
 * Scale for a percentage sparkline: the window's range with 20% headroom each
 * side, never narrower than MIN_PCT_SPAN points so scrape-to-scrape noise of a
 * tenth of a percent is not blown up to the full tile height, and kept inside 0-100.
 */
function fitRange(points: Spark["points"]): { lo: number; hi: number } {
  if (!points.length) return { lo: 0, hi: 100 };
  let lo = Math.min(...points.map((p) => p.v));
  let hi = Math.max(...points.map((p) => p.v));
  const pad = (hi - lo) * 0.2;
  lo -= pad;
  hi += pad;
  if (hi - lo < MIN_PCT_SPAN) {
    const mid = (lo + hi) / 2;
    lo = mid - MIN_PCT_SPAN / 2;
    hi = mid + MIN_PCT_SPAN / 2;
  }
  // Shift, don't squash, a range that pokes past 0 or 100
  if (lo < 0) [lo, hi] = [0, hi - lo];
  if (hi > 100) [lo, hi] = [Math.max(0, lo - (hi - 100)), 100];
  return { lo, hi };
}

/**
 * The template's tile delta ("+18% 1m" / "stable"): the last 10s against the
 * first, so one noisy scrape cannot flip it. Shown once the data covers most
 * of the window; a node that only just started reporting has no trend yet.
 */
function trend(points: Spark["points"]): { label?: string; up: boolean } {
  if (points.length < 2) return { up: false };
  const first = points[0].at;
  const last = points[points.length - 1].at;
  if (last - first < WINDOW_MS * 0.75) return { up: false };
  const avg = (ps: Spark["points"]) => ps.reduce((a, p) => a + p.v, 0) / ps.length;
  const change = avg(points.filter((p) => p.at >= last - TREND_EDGE_MS)) - avg(points.filter((p) => p.at <= first + TREND_EDGE_MS));
  if (Math.abs(change) < TREND_STEP) return { label: "stable", up: false };
  return { label: `${change > 0 ? "+" : "−"}${Math.abs(change).toFixed(0)}% ${LIVE_WINDOW_MINUTES}m`, up: change > 0 };
}

const ink = (pct: number) => `color-mix(in srgb, var(--color-text) ${pct}%, transparent)`;

function StreamStatus({ status, onResume, signInHref }: { status: Status; onResume: () => void; signInHref: string }) {
  const text: Record<Status, string> = {
    connecting: "connecting…",
    live: `live · last ${LIVE_WINDOW_MINUTES} min`,
    reconnecting: "reconnecting…",
    paused: "paused while the tab is hidden",
    evicted: "paused — live tiles are open in too many other tabs",
    "signed-out": "session expired",
    missing: "node no longer exists",
  };
  return (
    <div className="mono flex justify-end gap-2 px-6 text-[10.5px]" style={{ color: ink(45) }}>
      <span className="flex items-center gap-1.5">
        {status === "live" ? <span className="size-1.5 rounded-full" style={{ background: toneVar.ok }} aria-hidden /> : null}
        {text[status]}
      </span>
      {status === "evicted" ? (
        <button type="button" onClick={onResume} className="cursor-pointer underline" style={{ color: ink(70) }}>
          Resume
        </button>
      ) : null}
      {status === "signed-out" ? (
        <Link href={signInHref} className="underline" style={{ color: ink(70) }}>
          Sign in
        </Link>
      ) : null}
    </div>
  );
}
