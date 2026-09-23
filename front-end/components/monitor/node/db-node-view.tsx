import { formatAgo, formatBytes, formatPct, formatUptime, usageTone } from "@/src/lib/format";
import type { NodeAlert } from "@/src/lib/health";
import type { MountItem, ServerDetail } from "@/src/lib/types";
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
} from "./shared";

/**
 * Database node template, after the mockup's DB node view:
 * Overview · Sessions · Replication · Backups · Alert log.
 *
 * Only presence, uptime, storage and log-channel config come from the API
 * today. Panels the backend does not feed (sessions, top SQL, replication,
 * tablespaces, backups, log content) keep their place in the layout and say
 * what they are waiting for — a monitoring screen must not show invented
 * numbers.
 */

export const DB_TABS = [
  { id: "overview", label: "Overview" },
  { id: "sessions", label: "Sessions" },
  { id: "replication", label: "Replication" },
  { id: "backups", label: "Backups" },
  { id: "log", label: "Alert log" },
] as const;

export type DbTab = (typeof DB_TABS)[number]["id"];

const ink = (pct: number) => `color-mix(in srgb, var(--color-text) ${pct}%, transparent)`;

export function DbNodeView({ node, tab, file }: { node: ServerDetail; tab: DbTab; file?: string }) {
  const { mounts, alerts, statusLong, tone, headline } = nodeHealth(node);
  const basePath = `/dashboard/nodes/${node.uuid}`;

  return (
    <div>
      <NodeHeader
        crumb="DB nodes"
        name={node.name}
        kind="DB"
        statusLong={statusLong}
        tone={tone}
        headline={headline}
        meta={[node.ip, node.systemName, node.description].filter(Boolean).join(" · ")}
        sample={
          node.storage
            ? `storage sample ${clockOf(node.storage.recordedAt)} · ${formatAgo(node.storage.recordedAt)} · seen ${formatAgo(node.lastSeenAt)}`
            : `no storage sample yet · seen ${formatAgo(node.lastSeenAt)}`
        }
      />
      <NodeTabs basePath={basePath} tabs={[...DB_TABS]} active={tab} />

      {tab === "overview" ? <Overview node={node} mounts={mounts} alerts={alerts} /> : null}
      {tab === "sessions" ? <Sessions /> : null}
      {tab === "replication" ? <Replication /> : null}
      {tab === "backups" ? <Backups /> : null}
      {tab === "log" ? <LogTail node={node} basePath={basePath} file={file} /> : null}
    </div>
  );
}

function Overview({ node, mounts, alerts }: { node: ServerDetail; mounts: MountItem[]; alerts: NodeAlert[] }) {
  const st = node.storage;
  const measured = mounts.filter((m) => !m.error && m.usedPct != null);
  const busiest = measured.reduce<MountItem | null>((w, m) => (w == null || m.usedPct! > w.usedPct! ? m : w), null);
  const failed = mounts.length - measured.length;
  const hot = measured.filter((m) => usageTone(m.usedPct) === "warn" || usageTone(m.usedPct) === "crit").length;

  return (
    <div>
      <div className="grid grid-cols-2 gap-5 px-6 pt-[22px] pb-1.5 lg:grid-cols-4">
        <Tile
          label="Disk · host"
          value={formatPct(st?.usedPct)}
          delta={st ? `${formatBytes(st.freeBytes)} free` : undefined}
          deltaTone={st && usageTone(st.usedPct) !== "ok" ? toneVar[usageTone(st.usedPct)] : undefined}
          pct={st?.usedPct}
          foot={st ? `${formatBytes(st.usedBytes)} / ${formatBytes(st.totalBytes)}${st.partial ? " · partial" : ""}` : "no storage scrape"}
        />
        <Tile
          label={busiest ? `Disk ${busiest.mountPoint}` : "Busiest mount"}
          value={busiest ? formatPct(busiest.usedPct) : "—"}
          delta={busiest ? `${formatBytes(busiest.freeBytes)} free` : undefined}
          deltaTone={busiest && usageTone(busiest.usedPct) !== "ok" ? toneVar[usageTone(busiest.usedPct)] : undefined}
          pct={busiest?.usedPct}
          foot={busiest ? `${formatBytes(busiest.usedBytes)} / ${formatBytes(busiest.totalBytes)} · ${busiest.device ?? "—"}` : "no mounts measured"}
        />
        <Tile
          label="Filesystems"
          value={String(mounts.length)}
          delta={hot ? `${hot} above 75%` : failed ? undefined : "all healthy"}
          deltaTone={hot ? toneVar.warn : undefined}
          foot={failed ? `${failed} could not be measured` : "all measured"}
        />
        <Tile
          label="Uptime"
          value={formatUptime(node.uptimeSeconds)}
          delta={node.online === false ? "stale" : undefined}
          deltaTone={node.online === false ? toneVar.crit : undefined}
          foot={`seen ${formatAgo(node.lastSeenAt)}`}
        />
      </div>

      <div className="grid gap-6 px-6 pt-[18px] pb-7 lg:grid-cols-[minmax(0,1fr)_300px]">
        <div className="flex min-w-0 flex-col gap-6">
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

          <section>
            <SectionTitle title="Top SQL by elapsed time" />
            <table className="table">
              <thead>
                <tr>
                  <th className="w-5" />
                  <th>SQL id</th>
                  <th className="w-[130px]">Module</th>
                  <th className="w-[100px]">Execs</th>
                  <th className="w-[100px]">Elapsed</th>
                </tr>
              </thead>
            </table>
            <div className="mt-2">
              <NotInstrumented
                what="Top SQL is not collected for this node."
                needs="Needs a database agent feed (e.g. v$sqlstats / pg_stat_statements) published to the hub."
              />
            </div>
          </section>
        </div>

        <aside className="min-w-0">
          <AlertsRail alerts={alerts} />
          <FactsList
            facts={[
              { k: "System", v: node.systemName },
              { k: "Address", v: node.ip },
              { k: "Type", v: "Database" },
              { k: "Role", v: node.description ?? "—" },
              { k: "Presence", v: node.online === true ? "reporting" : node.online === false ? "not reporting" : "unknown" },
              { k: "Uptime", v: formatUptime(node.uptimeSeconds) },
              { k: "Last seen", v: formatAgo(node.lastSeenAt) },
              { k: "Capacity", v: st ? formatBytes(st.totalBytes) : "—" },
              { k: "Log channels", v: String(node.logs.length) },
              { k: "Registered", v: node.createdAt.slice(0, 10) },
            ]}
          />
        </aside>
      </div>
    </div>
  );
}

function Sessions() {
  return (
    <div className="grid gap-6 px-6 pt-5 pb-7 lg:grid-cols-[minmax(0,1fr)_300px]">
      <section className="min-w-0">
        <SectionTitle title="Blocking chain" />
        <div className="overflow-x-auto">
          <table className="table min-w-[560px]">
            <thead>
              <tr>
                <th className="w-[70px]">SID</th>
                <th>User / program</th>
                <th className="w-[88px]">Wait</th>
                <th className="w-[120px]">Event</th>
                <th>SQL</th>
              </tr>
            </thead>
          </table>
        </div>
        <div className="mt-2">
          <NotInstrumented
            what="Session and lock data is not collected for this node."
            needs="Needs the agent to publish active sessions and blockers (v$session / pg_stat_activity)."
          />
        </div>
      </section>
      <aside>
        <div className="mb-2 text-[10px] tracking-[.1em] uppercase" style={{ color: ink(50) }}>
          Sessions
        </div>
        <div className="mono text-[30px] leading-none" style={{ color: ink(35) }}>
          —<span className="text-[14px]"> / —</span>
        </div>
        <div className="mt-2 flex gap-[2px]" aria-hidden>
          {Array.from({ length: 30 }, (_, i) => (
            <div key={i} className="h-4 flex-1 bg-[var(--ink-08)]" />
          ))}
        </div>
        <div className="mt-1.5 flex justify-between text-[10.5px]" style={{ color: ink(50) }}>
          <span>active —</span>
          <span>idle —</span>
          <span>blocked —</span>
        </div>
      </aside>
    </div>
  );
}

function BlueprintCard({ title, meta, children }: { title: string; meta?: string; children: React.ReactNode }) {
  return (
    <div className="blueprint flex flex-col gap-2 p-3.5">
      <i className="corner tl" />
      <i className="corner tr" />
      <i className="corner bl" />
      <i className="corner br" />
      <div className="flex items-baseline justify-between">
        <span className="text-[10px] tracking-[.1em] uppercase" style={{ color: ink(55) }}>
          {title}
        </span>
        {meta ? <span className="mono text-[11px]">{meta}</span> : null}
      </div>
      {children}
    </div>
  );
}

function Replication() {
  return (
    <div className="grid gap-[22px] px-6 pt-5 pb-7 md:grid-cols-2">
      <BlueprintCard title="Replication lag">
        <svg viewBox="0 0 240 90" preserveAspectRatio="none" className="block h-[120px] w-full" aria-hidden>
          <line x1="0" y1="89" x2="240" y2="89" stroke="var(--color-divider)" strokeWidth="1" />
          <line x1="0" y1="26" x2="240" y2="26" stroke="var(--color-divider)" strokeWidth="1" strokeDasharray="4 4" />
        </svg>
        <NotInstrumented what="No replication lag feed." needs="Needs standby apply/transport lag from the agent (v$dataguard_stats / pg_stat_replication)." />
      </BlueprintCard>
      <BlueprintCard title="Tablespaces">
        <NotInstrumented what="Tablespace usage is not collected." needs="Filesystem usage is on the Overview tab; tablespace fill needs a database agent feed." />
      </BlueprintCard>
    </div>
  );
}

function Backups() {
  return (
    <div className="px-6 pt-5 pb-7">
      <SectionTitle title="Backups · last 14 days" />
      <div className="flex gap-1.5" aria-hidden>
        {Array.from({ length: 14 }, (_, i) => (
          <div key={i} className="flex flex-1 flex-col items-center gap-[5px]">
            <div className="h-16 w-full border border-dashed border-divider" />
            <div className="mono text-[10px]" style={{ color: ink(50) }}>
              {14 - i}d
            </div>
          </div>
        ))}
      </div>
      <div className="mt-3">
        <NotInstrumented what="Backup history is not collected for this node." needs="Needs RMAN / pg_basebackup job results published to the hub." />
      </div>
    </div>
  );
}
