import Link from "next/link";
import { formatAgo, formatBytes, formatPct, usageTone, type Tone } from "@/src/lib/format";
import { mountAlerts, nodeAlerts, severityRank, statusLabel, worst, type NodeAlert, type Severity } from "@/src/lib/health";
import type { MountItem, ServerDetail } from "@/src/lib/types";
import { Empty } from "../indicators";
import { LiveLogTail } from "./live-log-tail";

/**
 * Building blocks of the node page, shared by the DB and App/Web templates.
 * Sizes, padding and margins follow template/Node Monitor.html (node view)
 * value for value — check against it before changing one.
 */

export const toneVar: Record<Tone, string> = {
  ok: "var(--color-ok)",
  warn: "var(--color-warn)",
  crit: "var(--color-crit)",
  muted: "var(--ink-45)",
};

const ink = (pct: number) => `color-mix(in srgb, var(--color-text) ${pct}%, transparent)`;

/** Wall-clock part of an offset ISO timestamp, in the zone the API rendered it in. */
export function clockOf(iso: string | null | undefined): string {
  if (!iso) return "—";
  const m = /T(\d{2}:\d{2}:\d{2})(?:\.\d+)?(Z|[+-]\d{2}:\d{2})?/.exec(iso);
  return m ? `${m[1]}${m[2] && m[2] !== "Z" ? ` ${m[2]}` : " UTC"}` : "—";
}

const statusLong: Record<string, string> = {
  OK: "HEALTHY",
  WARN: "WARNING",
  CRIT: "CRITICAL",
  DOWN: "DOWN",
  UNKNOWN: "UNKNOWN",
};

/**
 * Alerts and header status for a node page: presence, per-mount storage
 * (instead of the host rollup, since mounts name what is filling) and any
 * kind-specific alerts the caller adds. Worst first.
 */
export function nodeHealth(node: ServerDetail, extra: NodeAlert[] = []) {
  const mounts = node.storage?.mounts ?? [];
  const alerts = [
    ...nodeAlerts(node, { rollupDisk: mounts.length === 0 }),
    ...mountAlerts(mounts, node.storage?.recordedAt ?? null),
    ...extra,
  ].sort((a, b) => severityRank[b.severity] - severityRank[a.severity]);
  const severity = worst(alerts);
  const status = statusLabel(node.online, severity);
  return {
    mounts,
    alerts,
    statusLong: statusLong[status] ?? status,
    tone: node.online == null ? "var(--ink-45)" : node.online === false ? toneVar.crit : toneVar[severity],
    headline:
      node.online === false
        ? `not reporting · last seen ${formatAgo(node.lastSeenAt)}`
        : (alerts[0]?.message ?? "all checks passing"),
  };
}

export function NodeHeader({
  crumb,
  name,
  kind,
  statusLong,
  tone,
  headline,
  meta,
  sample,
}: {
  crumb: string;
  name: string;
  kind: string;
  statusLong: string;
  tone: string;
  headline: string;
  meta: string;
  sample: string;
}) {
  return (
    <div className="flex items-start gap-[18px] border-b border-divider px-6 py-4">
      <div className="min-w-0">
        <Link
          href="/dashboard"
          className="mb-[2px] inline-block text-[11px] tracking-[.08em] text-accent-700 uppercase no-underline hover:text-accent-600"
        >
          ← Server / {crumb}
        </Link>
        <div className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1">
          <h1 className="m-0 text-[24px] tracking-[.005em]">{name}</h1>
          <span className="mono border border-divider px-1.5 py-[2px] text-[9.5px] tracking-[.08em]">{kind}</span>
          <span className="mono px-1.5 py-[3px] text-[9.5px] tracking-[.08em] text-[#f2f2f3]" style={{ background: tone }}>
            {statusLong}
          </span>
          <span className="text-[12px]" style={{ color: ink(55) }}>
            {headline}
          </span>
        </div>
        <div className="text-[12px]" style={{ color: ink(55) }}>
          {meta}
        </div>
        <div className="mono mt-[2px] text-[10.5px]" style={{ color: ink(45) }}>
          {sample}
        </div>
      </div>
    </div>
  );
}

/** Tab strip. Tabs are links (`?tab=`) so the page stays a server component and each tab is linkable. */
export function NodeTabs({
  basePath,
  tabs,
  active,
}: {
  basePath: string;
  tabs: { id: string; label: string }[];
  active: string;
}) {
  return (
    <nav className="flex overflow-x-auto border-b border-divider px-6">
      {tabs.map((t) => {
        const on = t.id === active;
        return (
          <Link
            key={t.id}
            href={t.id === "overview" ? basePath : `${basePath}?tab=${t.id}`}
            scroll={false}
            aria-current={on ? "page" : undefined}
            className={`font-heading border-b-2 px-[18px] py-3 text-[14px] font-semibold tracking-[.02em] whitespace-nowrap no-underline hover:bg-[color-mix(in_srgb,var(--color-accent)_10%,transparent)] ${
              on ? "border-accent text-ink" : "border-transparent"
            }`}
            style={on ? undefined : { color: ink(55) }}
          >
            {t.label}
          </Link>
        );
      })}
    </nav>
  );
}

/** A time series for a tile sparkline, positioned by time so reporting gaps stay visible. */
export type Spark = {
  points: { at: number; v: number }[];
  from: number;
  to: number;
  /** Top of the scale: 100 for percentages, the series peak for rates. */
  max: number;
};

const SPARK_W = 180;
const SPARK_H = 44;

/** The template's tile sparkline: accent line over a 14% area fill. */
function Sparkline({ spark }: { spark: Spark }) {
  const span = Math.max(1, spark.to - spark.from);
  const max = spark.max > 0 ? spark.max : 1;
  const xy = spark.points.map((p) => {
    const x = ((p.at - spark.from) / span) * SPARK_W;
    const y = SPARK_H - (Math.min(max, Math.max(0, p.v)) / max) * SPARK_H;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  const first = xy[0]?.split(",")[0] ?? "0";
  const last = xy[xy.length - 1]?.split(",")[0] ?? String(SPARK_W);
  return (
    <svg viewBox={`0 0 ${SPARK_W} ${SPARK_H}`} preserveAspectRatio="none" className="block h-[44px] w-full" aria-hidden>
      <polygon points={`${first},${SPARK_H} ${xy.join(" ")} ${last},${SPARK_H}`} fill="color-mix(in srgb, #5980a6 14%, transparent)" />
      <polyline points={xy.join(" ")} fill="none" stroke="#5980a6" strokeWidth="1.4" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

/**
 * Metric tile. Draws a sparkline when history is available (two or more
 * points), else a usage bar for fill-level metrics, else an empty slot of
 * the same height — never a made-up trend.
 */
export function Tile({
  label,
  value,
  delta,
  deltaTone,
  pct,
  spark,
  foot,
}: {
  label: string;
  value: string;
  delta?: string;
  deltaTone?: string;
  pct?: number | null;
  spark?: Spark | null;
  foot: string;
}) {
  const fill = pct == null ? null : Math.min(100, Math.max(0, pct));
  const hasSpark = spark != null && spark.points.length >= 2;
  return (
    <div className="flex flex-col gap-1.5 rounded-[5px] border border-divider bg-white p-[13px] shadow-[var(--shadow-sm)]">
      <div className="flex items-baseline justify-between gap-2">
        <span className="font-heading text-[11px] font-semibold tracking-[.06em] uppercase" style={{ color: ink(60) }}>
          {label}
        </span>
        {delta ? (
          <span className="mono text-[10.5px] whitespace-nowrap" style={{ color: deltaTone ?? ink(50) }}>
            {delta}
          </span>
        ) : null}
      </div>
      <div className="mono text-[27px] leading-none">{value}</div>
      {/* Same 44px slot the template gives its sparkline, so tiles keep their height. */}
      <div className="flex h-[44px] items-end">
        {hasSpark ? (
          <Sparkline spark={spark} />
        ) : fill != null ? (
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-[color-mix(in_srgb,var(--color-text)_10%,transparent)]">
            <div className="h-full rounded-full" style={{ width: `${fill}%`, background: toneVar[usageTone(fill)] }} />
          </div>
        ) : null}
      </div>
      <div className="mono truncate text-[10px]" style={{ color: ink(45) }}>
        {foot}
      </div>
    </div>
  );
}

export function SectionTitle({ title, meta, children }: { title: string; meta?: string; children?: React.ReactNode }) {
  return (
    <div className="mb-[9px] flex flex-wrap items-baseline gap-x-3 gap-y-1">
      <h4 className="m-0">{title}</h4>
      {meta ? (
        <span className="mono text-[11.5px]" style={{ color: ink(50) }}>
          {meta}
        </span>
      ) : null}
      {children ? <div className="ml-auto flex gap-2">{children}</div> : null}
    </div>
  );
}

/**
 * Placeholder for a mockup panel the backend does not feed yet. Says what is
 * missing instead of showing invented figures.
 */
export function NotInstrumented({ what, needs }: { what: string; needs: string }) {
  return (
    <div className="rounded-[5px] border border-dashed border-divider bg-[color-mix(in_srgb,var(--color-chrome)_45%,#ffffff)] px-4 py-5">
      <div className="text-[10px] tracking-[.1em] uppercase" style={{ color: ink(50) }}>
        Not instrumented
      </div>
      <div className="mt-1 text-[12px]">{what}</div>
      <div className="mt-0.5 text-[12px]" style={{ color: ink(55) }}>
        {needs}
      </div>
    </div>
  );
}

const STORAGE_COLS =
  "grid grid-cols-[18px_minmax(96px,1.1fr)_minmax(130px,1.5fr)_58px_76px_76px_76px_minmax(120px,1.4fr)] items-center gap-2.5";

/** Filesystem table in the mockup's card-style grid. */
export function StorageTable({ mounts }: { mounts: MountItem[] }) {
  return (
    <div className="overflow-x-auto rounded-[5px] border border-divider bg-white shadow-[var(--shadow-sm)]">
      <div className="min-w-[760px]">
        <div
          className={`${STORAGE_COLS} font-heading border-b border-divider bg-[color-mix(in_srgb,var(--color-chrome)_70%,#ffffff)] px-3.5 py-2 text-[10.5px] font-semibold tracking-[.06em] uppercase`}
          style={{ color: ink(58) }}
        >
          <div />
          <div>Mount</div>
          <div>Device</div>
          <div>Type</div>
          <div className="text-right">Size</div>
          <div className="text-right">Used</div>
          <div className="text-right">Free</div>
          <div>Usage</div>
        </div>
        {mounts.map((m) => {
          const tone = m.error ? "warn" : usageTone(m.usedPct);
          const fill = m.usedPct == null ? 0 : Math.min(100, Math.max(0, m.usedPct));
          return (
            <div
              key={m.mountPoint}
              className={`${STORAGE_COLS} border-b border-[color-mix(in_srgb,var(--color-text)_7%,transparent)] px-3.5 py-[9px] last:border-b-0 hover:bg-[color-mix(in_srgb,var(--color-accent)_5%,transparent)]`}
            >
              <div className="size-[7px] rounded-full" style={{ background: toneVar[tone] }} />
              <div className="mono truncate text-[12.5px]">{m.mountPoint}</div>
              <div className="mono truncate text-[11px]" style={{ color: ink(55) }}>
                {m.device ?? "—"}
              </div>
              <div className="mono text-[11px]" style={{ color: ink(65) }}>
                {m.fstype ?? "—"}
              </div>
              {m.error ? (
                // An unmeasurable mount is a monitoring signal, not missing data.
                <div className="col-span-4 text-[12px] text-warn">{m.error}</div>
              ) : (
                <>
                  <div className="mono text-right text-[12px]">{formatBytes(m.totalBytes)}</div>
                  <div className="mono text-right text-[12px]">{formatBytes(m.usedBytes)}</div>
                  <div className="mono text-right text-[12px]" style={{ color: ink(60) }}>
                    {formatBytes(m.freeBytes)}
                  </div>
                  <div className="flex items-center gap-[9px]">
                    <div className="h-1.5 min-w-12 flex-1 overflow-hidden rounded-full bg-[color-mix(in_srgb,var(--color-text)_10%,transparent)]">
                      <div className="h-full rounded-full" style={{ width: `${fill}%`, background: toneVar[tone] }} />
                    </div>
                    <div className="mono w-[42px] flex-none text-right text-[12px]">{formatPct(m.usedPct)}</div>
                  </div>
                </>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

const sevText: Record<Severity, string> = { ok: "OK", warn: "WARNING", crit: "CRITICAL" };

/** Right-rail "Open alerts" list, or the all-clear card. */
export function AlertsRail({ alerts }: { alerts: NodeAlert[] }) {
  return (
    <>
      <h4 className="m-0 mb-2">Open alerts</h4>
      {alerts.length === 0 ? (
        <div
          className="mb-[22px] flex items-center gap-2 rounded-[5px] border border-divider bg-white px-[11px] py-[9px] text-[12px] shadow-[var(--shadow-sm)]"
          style={{ color: ink(55) }}
        >
          <span className="size-[7px] flex-none rounded-full bg-ok" />
          <span>All checks passing on this node.</span>
        </div>
      ) : (
        <div className="mb-[22px] flex flex-col gap-1.5">
          {alerts.map((a, i) => (
            <div key={i} className="rounded-[5px] border border-divider bg-white px-[11px] py-2 shadow-[var(--shadow-sm)]">
              <div className="flex items-center gap-2">
                <span className="size-1.5 flex-none rounded-full" style={{ background: toneVar[a.severity] }} />
                <span className="text-[10px] font-semibold tracking-[.04em]" style={{ color: toneVar[a.severity] }}>
                  {sevText[a.severity]}
                </span>
                <span className="mono ml-auto text-[10.5px]" style={{ color: ink(50) }}>
                  {a.observed}
                </span>
              </div>
              <div className="mt-[2px] text-[12px] leading-[1.4] text-pretty">{a.message}</div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}

export function FactsList({ facts }: { facts: { k: string; v: React.ReactNode }[] }) {
  return (
    <>
      <h4 className="m-0 mb-2">Facts</h4>
      <dl>
        {facts.map((f) => (
          <div key={f.k} className="flex justify-between gap-2.5 border-b border-[var(--ink-08)] py-[5px] text-[12.5px]">
            <dt style={{ color: ink(55) }}>{f.k}</dt>
            <dd className="mono min-w-0 text-right break-all">{f.v}</dd>
          </div>
        ))}
      </dl>
    </>
  );
}

/**
 * Log tail tab, shared by every node kind: the node's log channels on the
 * left (one tails at a time, picked with `?file=`), the dark panel on the right.
 */
export function LogTail({ node, basePath, file }: { node: ServerDetail; basePath: string; file?: string }) {
  const active = node.logs.find((l) => l.uuid === file) ?? node.logs[0];

  return (
    <div className="px-6 pt-5 pb-7">
      <SectionTitle title="Log tail" meta={active?.pubPath} />
      <div className="grid items-start gap-[18px] md:grid-cols-[250px_minmax(0,1fr)]">
        <div>
          <div className="mb-2 text-[10px] tracking-[.1em] uppercase" style={{ color: ink(50) }}>
            Files on this node
          </div>
          {node.logs.length === 0 ? (
            <Empty>No log channels configured.</Empty>
          ) : (
            <div className="flex flex-col gap-0.5 rounded-lg border border-divider bg-white p-1 shadow-[var(--shadow-sm)]">
              {node.logs.map((l) => {
                const on = l.uuid === active?.uuid;
                return (
                  <Link
                    key={l.uuid}
                    href={`${basePath}?tab=log&file=${encodeURIComponent(l.uuid)}`}
                    scroll={false}
                    aria-current={on ? "true" : undefined}
                    className="pressable flex items-start gap-[9px] rounded-md px-[10px] py-[8px] no-underline"
                    style={on ? { background: "color-mix(in srgb, var(--color-accent) 12%, transparent)" } : undefined}
                  >
                    <span className="mt-[3px] flex size-[11px] flex-none items-center justify-center rounded-full border border-accent">
                      <span className="size-[5px] rounded-full" style={{ background: on ? "var(--color-accent)" : "transparent" }} />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className={`mono block text-[12.5px] whitespace-nowrap ${on ? "text-accent-700" : ""}`} style={on ? undefined : { color: ink(55) }}>
                        {l.channel}
                      </span>
                      <span className="mono block truncate text-[10px]" style={{ color: ink(45) }}>
                        {l.pubPath}
                      </span>
                    </span>
                  </Link>
                );
              })}
            </div>
          )}
          <div className="mt-2 text-[11px]" style={{ color: ink(50) }}>
            One file tails at a time — pick another to switch.
          </div>
        </div>

        <div className="overflow-hidden rounded-lg bg-accent-900 shadow-[var(--shadow-sm)]">
          {active ? (
            // Keyed so picking another file drops the old stream and opens a new one
            <LiveLogTail key={active.uuid} channel={active} />
          ) : (
            <div className="mono min-h-[200px] px-4 py-3.5 text-[12px] leading-[1.75] text-[color-mix(in_srgb,#f2f2f3_50%,transparent)]">
              No log channel selected.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
