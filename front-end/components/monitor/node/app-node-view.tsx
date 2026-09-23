import { formatAgo, formatBytes, formatPct, formatRate, formatUptime, usageTone } from "@/src/lib/format";
import { resourceAlerts } from "@/src/lib/health";
import type { MountItem, ResourceHistory, ResourcePoint, ServerDetail } from "@/src/lib/types";
import { Empty } from "../indicators";
import {
  AlertsRail,
  FactsList,
  LogTail,
  NodeHeader,
  NodeTabs,
  NotInstrumented,
  SectionTitle,
  StorageTable,
  Tile,
  clockOf,
  nodeHealth,
  toneVar,
  type Spark,
} from "./shared";

/**
 * App & Web node template, after the mockup's app node view:
 * Overview (CPU · Memory · Disk · Network tiles, containers, storage, alerts
 * and facts) and Log tail.
 *
 * CPU, memory, swap and network come from the latest resource scrape and the
 * averaged history behind the sparklines. Containers have no feed yet (the
 * hub ignores the docker channel), so that panel says so instead of
 * showing invented figures.
 */

export const APP_TABS = [
  { id: "overview", label: "Overview" },
  { id: "log", label: "Log tail" },
] as const;

export type AppTab = (typeof APP_TABS)[number]["id"];

/** Window the page asks the history endpoint for, and labels trends with. */
export const HISTORY_MINUTES = 60;

/** Change over the window below which a metric reads "stable". */
const TREND_STEP = 5;

export function AppNodeView({
  node,
  tab,
  file,
  history,
}: {
  node: ServerDetail;
  tab: AppTab;
  file?: string;
  /** Null when not fetched or the request failed; tiles then fall back to bars. */
  history: ResourceHistory | null;
}) {
  const res = node.resources;
  const { mounts, alerts, statusLong, tone, headline } = nodeHealth(node, resourceAlerts(res, history?.points ?? null));
  const basePath = `/dashboard/nodes/${node.uuid}`;
  const web = node.serverType === "WEB";

  return (
    <div>
      <NodeHeader
        crumb={web ? "Web nodes" : "App nodes"}
        name={node.name}
        kind={web ? "WEB" : "APP"}
        statusLong={statusLong}
        tone={tone}
        headline={headline}
        meta={[node.ip, hardware(node), node.systemName, node.description].filter(Boolean).join(" · ")}
        sample={
          res
            ? `agent sample ${clockOf(res.recordedAt)} · ${formatAgo(res.recordedAt)} · seen ${formatAgo(node.lastSeenAt)}`
            : `no agent sample yet · seen ${formatAgo(node.lastSeenAt)}`
        }
      />
      <NodeTabs basePath={basePath} tabs={[...APP_TABS]} active={tab} />

      {tab === "overview" ? (
        <Overview node={node} mounts={mounts} alerts={alerts} history={history} />
      ) : null}
      {tab === "log" ? <LogTail node={node} basePath={basePath} file={file} /> : null}
    </div>
  );
}

function hardware(node: ServerDetail): string | null {
  const r = node.resources;
  if (!r || (r.cpuCount == null && r.memTotalBytes == null)) return null;
  return `${r.cpuCount ?? "—"} vCPU / ${formatBytes(r.memTotalBytes)}`;
}

function Overview({
  node,
  mounts,
  alerts,
  history,
}: {
  node: ServerDetail;
  mounts: MountItem[];
  alerts: ReturnType<typeof nodeHealth>["alerts"];
  history: ResourceHistory | null;
}) {
  const res = node.resources;
  const st = node.storage;
  const stale = node.online === false;
  const series = (pick: (p: ResourcePoint) => number | null) =>
    (history?.points ?? []).flatMap((p) => {
      const v = pick(p);
      return v == null ? [] : [{ at: new Date(p.t).getTime(), v }];
    });
  const cpuPts = series((p) => p.cpuPct);
  const memPts = series((p) => p.memPct);
  const netPts = series((p) => (p.netRxBps == null && p.netTxBps == null ? null : (p.netRxBps ?? 0) + (p.netTxBps ?? 0)));
  const netPeak = netPts.reduce((m, p) => Math.max(m, p.v), 0);

  const spark = (points: Spark["points"], max: number): Spark | null =>
    history ? { points, from: history.from, to: history.to, max } : null;

  // The template's disk tile shows the root filesystem; fall back to the
  // fullest mount when the agent does not report "/".
  const measured = mounts.filter((m) => !m.error && m.usedPct != null);
  const root =
    measured.find((m) => m.mountPoint === "/") ??
    measured.reduce<MountItem | null>((w, m) => (w == null || m.usedPct! > w.usedPct! ? m : w), null);
  const hot = measured.filter((m) => usageTone(m.usedPct) === "warn" || usageTone(m.usedPct) === "crit").length;

  const cpuTrend = trend(cpuPts);
  const memTrend = trend(memPts);
  const load = [res?.load1, res?.load5, res?.load15].map((l) => (l == null ? "—" : l.toFixed(2))).join(" / ");

  return (
    <div>
      <div className="grid grid-cols-2 gap-5 px-6 pt-[22px] pb-1.5 lg:grid-cols-4">
        <Tile
          label="CPU"
          value={formatPct(res?.cpuPct)}
          delta={stale ? "stale" : cpuTrend.label}
          deltaTone={stale || cpuTrend.up ? toneVar.crit : undefined}
          spark={spark(cpuPts, 100)}
          pct={res?.cpuPct}
          foot={res ? `${res.cpuCount ?? "—"} vCPU · load ${load}` : "no resource scrape"}
        />
        <Tile
          label="Memory"
          value={formatPct(res?.memUsedPct)}
          delta={stale ? "stale" : memTrend.label}
          deltaTone={stale || memTrend.up ? toneVar.crit : undefined}
          spark={spark(memPts, 100)}
          pct={res?.memUsedPct}
          foot={
            res
              ? `${formatBytes(res.memUsedBytes)} / ${formatBytes(res.memTotalBytes)} · swap ${formatBytes(res.swapUsedBytes)}${
                  res.swapUsedPct != null ? ` (${res.swapUsedPct.toFixed(0)}%)` : ""
                }${res.memEstimated ? " · est." : ""}`
              : "no resource scrape"
          }
        />
        <Tile
          label={root ? `Disk ${root.mountPoint}` : "Disk"}
          value={root ? formatPct(root.usedPct) : formatPct(st?.usedPct)}
          delta={root ? `${formatBytes(root.freeBytes)} free` : undefined}
          deltaTone={root && usageTone(root.usedPct) !== "ok" ? toneVar[usageTone(root.usedPct)] : undefined}
          pct={root?.usedPct ?? st?.usedPct}
          foot={root ? `${formatBytes(root.usedBytes)} / ${formatBytes(root.totalBytes)}` : "no storage scrape"}
        />
        <Tile
          label="Network"
          value={res && (res.netRxBps != null || res.netTxBps != null) ? formatRate((res.netRxBps ?? 0) + (res.netTxBps ?? 0)) : "—"}
          delta={stale ? "stale" : "rx + tx"}
          deltaTone={stale ? toneVar.crit : undefined}
          // Rates have no natural ceiling: scale to the window's peak, with headroom.
          spark={spark(netPts, netPeak * 1.15)}
          foot={res ? `rx ${formatRate(res.netRxBps)} · tx ${formatRate(res.netTxBps)}` : "no resource scrape"}
        />
      </div>

      <div className="grid gap-6 px-6 pt-[18px] pb-7 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div className="flex min-w-0 flex-col gap-6">
          <section>
            <SectionTitle title="Containers" />
            <NotInstrumented
              what="Container state is not collected for this node."
              needs="The agent can publish on the docker-* channel, but the hub does not ingest it yet."
            />
          </section>

          <section>
            <SectionTitle
              title="Storage"
              meta={
                mounts.length
                  ? `${hot} of ${mounts.length} partitions above 75%${st?.recordedAt ? ` · sampled ${formatAgo(st.recordedAt)}` : ""}`
                  : undefined
              }
            />
            {st?.partial ? (
              <p className="mb-2 text-[11px] text-warn">Rollup is partial — some filesystems were not measured.</p>
            ) : null}
            {mounts.length ? <StorageTable mounts={mounts} /> : <Empty>No storage scrape received from this node yet.</Empty>}
          </section>
        </div>

        <aside className="min-w-0">
          <AlertsRail alerts={alerts} />
          <FactsList
            facts={[
              { k: "System", v: node.systemName },
              { k: "Address", v: node.ip },
              { k: "Type", v: node.serverType === "WEB" ? "Web" : "App" },
              { k: "Role", v: node.description ?? "—" },
              { k: "Hardware", v: hardware(node) ?? "—" },
              {
                k: "Swap",
                v: res?.swapTotalBytes ? `${formatBytes(res.swapUsedBytes)} / ${formatBytes(res.swapTotalBytes)}` : "—",
              },
              { k: "Uptime", v: formatUptime(node.uptimeSeconds) },
              { k: "Sampled", v: clockOf(res?.recordedAt) },
              { k: "Log channels", v: String(node.logs.length) },
              { k: "Registered", v: node.createdAt.slice(0, 10) },
            ]}
          />
        </aside>
      </div>
    </div>
  );
}

/**
 * The template's tile delta ("+18% 1h" / "stable"): latest bucket against
 * the start of the window. `up` marks a rise worth colouring.
 */
function trend(points: Spark["points"]): { label?: string; up: boolean } {
  if (points.length < 2) return { up: false };
  const head = points.slice(0, 3);
  const base = head.reduce((a, p) => a + p.v, 0) / head.length;
  const change = points[points.length - 1].v - base;
  const window = HISTORY_MINUTES >= 60 ? `${HISTORY_MINUTES / 60}h` : `${HISTORY_MINUTES}m`;
  if (Math.abs(change) < TREND_STEP) return { label: "stable", up: false };
  return { label: `${change > 0 ? "+" : "−"}${Math.abs(change).toFixed(0)}% ${window}`, up: change > 0 };
}
