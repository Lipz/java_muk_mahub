import { formatAgo, formatBytes, formatPct, formatUptime, usageTone } from "@/src/lib/format";
import { resourceAlerts } from "@/src/lib/health";
import type { MountItem, ResourceHistory, ServerDetail } from "@/src/lib/types";
import { Empty } from "../indicators";
import { LiveResourceTiles } from "./live-resource-tiles";
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
} from "./shared";

/**
 * App & Web node template, after the mockup's app node view:
 * Overview (CPU · Memory · Disk · Network tiles, containers, storage, alerts
 * and facts) and Log tail.
 *
 * CPU, memory, swap and network follow the node live (LiveResourceTiles),
 * seeded from the latest scrape and history at page load. Containers have no feed yet (the
 * hub ignores the docker channel), so that panel says so instead of
 * showing invented figures.
 */

export const APP_TABS = [
  { id: "overview", label: "Overview" },
  { id: "log", label: "Log tail" },
] as const;

export type AppTab = (typeof APP_TABS)[number]["id"];

export function AppNodeView({
  node,
  tab,
  file,
  history,
}: {
  node: ServerDetail;
  tab: AppTab;
  file?: string;
  /** Null when not fetched or the request failed; tiles fill from the live stream instead. */
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

  // The template's disk tile shows the root filesystem; fall back to the
  // fullest mount when the agent does not report "/".
  const measured = mounts.filter((m) => !m.error && m.usedPct != null);
  const root =
    measured.find((m) => m.mountPoint === "/") ??
    measured.reduce<MountItem | null>((w, m) => (w == null || m.usedPct! > w.usedPct! ? m : w), null);
  const hot = measured.filter((m) => usageTone(m.usedPct) === "warn" || usageTone(m.usedPct) === "crit").length;


  return (
    <div>
      <LiveResourceTiles
        uuid={node.uuid}
        initial={res}
        lastSeenAt={node.online === false ? null : node.lastSeenAt}
        history={history}
        order={["cpu", "memory", "disk", "network"]}
        disk={
          <Tile
            label={root ? `Disk ${root.mountPoint}` : "Disk"}
            value={root ? formatPct(root.usedPct) : formatPct(st?.usedPct)}
            delta={root ? `${formatBytes(root.freeBytes)} free` : undefined}
            deltaTone={root && usageTone(root.usedPct) !== "ok" ? toneVar[usageTone(root.usedPct)] : undefined}
            pct={root?.usedPct ?? st?.usedPct}
            foot={root ? `${formatBytes(root.usedBytes)} / ${formatBytes(root.totalBytes)}` : "no storage scrape"}
          />
        }
      />

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
