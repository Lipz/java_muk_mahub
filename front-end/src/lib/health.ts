import { formatAgo, formatBytes, formatUptime, usageTone } from "./format";
import type { MountItem, ResourcePoint, ResourceSnapshot, ServerGroup, ServerSummary, ServerType } from "./types";

/**
 * Health derived from what the backend actually reports — presence and the
 * storage rollup. The mockup's alert feed has no backend yet, so alerts here
 * are computed from real signals rather than invented.
 *
 * Pure and framework-free. `buildFleet` formats relative times, so call it on
 * the server and hand the result to client components: formatting "12s ago"
 * during hydration would mismatch the server render.
 */

export type Severity = "ok" | "warn" | "crit";

export const severityRank: Record<Severity, number> = { ok: 0, warn: 1, crit: 2 };

export type NodeAlert = {
  severity: Severity;
  message: string;
  /** When the signal was observed, already formatted ("4m ago"). */
  observed: string;
};

export type FleetNode = {
  uuid: string;
  name: string;
  ip: string;
  kind: ServerType;
  role: string | null;
  systemId: string;
  systemName: string;
  online: boolean | null;
  severity: Severity;
  statusLabel: string;
  diskPct: number | null;
  diskAbs: string;
  uptime: string;
  seen: string;
  alerts: NodeAlert[];
  alertLabel: string;
};

export type FleetGroup = {
  systemId: string;
  systemName: string;
  nodes: FleetNode[];
};

export const kindLabel: Record<ServerType, string> = {
  APP: "App",
  WEB: "Web",
  DATABASE: "DB",
};

/**
 * `rollupDisk: false` drops the host-level disk alert — used where per-mount
 * alerts are shown instead, since they name the filesystem that is filling.
 */
export function nodeAlerts(s: ServerSummary, { rollupDisk = true } = {}): NodeAlert[] {
  const out: NodeAlert[] = [];
  // online === null means the hub could not ask Redis — a monitor outage,
  // not a node fault. Raising it per node would page on every server at once.
  if (s.online === false) {
    out.push({
      severity: "crit",
      message: s.lastSeenAt ? "Not reporting — no scrape received" : "Never reported",
      observed: formatAgo(s.lastSeenAt),
    });
  }
  const st = s.storage;
  if (st) {
    const tone = usageTone(st.usedPct);
    if (rollupDisk && (tone === "crit" || tone === "warn")) {
      out.push({
        severity: tone,
        message: `Disk ${st.usedPct!.toFixed(0)}% used · ${formatBytes(st.freeBytes)} free`,
        observed: formatAgo(st.recordedAt),
      });
    }
    if (st.partial) {
      out.push({
        severity: "warn",
        message: "Storage rollup partial — some mounts not measured",
        observed: formatAgo(st.recordedAt),
      });
    }
  }
  return out;
}

export function worst(alerts: NodeAlert[]): Severity {
  return alerts.reduce<Severity>(
    (w, a) => (severityRank[a.severity] > severityRank[w] ? a.severity : w),
    "ok",
  );
}

function alertLabel(alerts: NodeAlert[]): string {
  const crit = alerts.filter((a) => a.severity === "crit").length;
  const warn = alerts.length - crit;
  const parts = [];
  if (crit) parts.push(`${crit} critical`);
  if (warn) parts.push(`${warn} warning${warn === 1 ? "" : "s"}`);
  return parts.length ? parts.join(" · ") : "—";
}

/**
 * Per-mount alerts, for the node page where every filesystem is known.
 * A mount the agent could not measure is itself a signal and is raised.
 */
export function mountAlerts(mounts: MountItem[], recordedAt: string | null): NodeAlert[] {
  const observed = formatAgo(recordedAt);
  return mounts.flatMap<NodeAlert>((m) => {
    if (m.error) return [{ severity: "warn", message: `${m.mountPoint} not measured — ${m.error}`, observed }];
    const tone = usageTone(m.usedPct);
    if (tone !== "crit" && tone !== "warn") return [];
    return [{ severity: tone, message: `${m.mountPoint} ${m.usedPct!.toFixed(0)}% used · ${formatBytes(m.freeBytes)} free`, observed }];
  });
}

/** Resource alert thresholds, in percent. */
const MEM_WARN = 80;
const MEM_CRIT = 90;
const CPU_WARN = 80;
const CPU_CRIT = 90;
/** Buckets averaged for the CPU alert — ~10 minutes at the default 2-minute bucket. */
const CPU_WINDOW = 5;

/**
 * CPU and memory alerts for the node page.
 *
 * Memory is read from the latest scrape: it moves slowly, so one sample is
 * representative. CPU is not — a single 3-second scrape can catch a burst —
 * so it is judged on the average of the most recent history buckets, and
 * not raised at all without history. Swap is only a note on a memory alert;
 * swap in use on its own is normal on many hosts.
 */
export function resourceAlerts(res: ResourceSnapshot | null, history: ResourcePoint[] | null): NodeAlert[] {
  const out: NodeAlert[] = [];
  if (res?.memUsedPct != null && res.memUsedPct >= MEM_WARN) {
    const swapping = (res.swapUsedBytes ?? 0) > 0;
    out.push({
      severity: res.memUsedPct >= MEM_CRIT ? "crit" : "warn",
      message: `Memory ${res.memUsedPct.toFixed(0)}%${swapping ? " · swap in use" : ""}`,
      observed: formatAgo(res.recordedAt),
    });
  }
  const recent = (history ?? []).slice(-CPU_WINDOW).flatMap((p) => (p.cpuPct == null ? [] : [p.cpuPct]));
  if (recent.length) {
    const avg = recent.reduce((a, v) => a + v, 0) / recent.length;
    if (avg >= CPU_WARN) {
      out.push({
        severity: avg >= CPU_CRIT ? "crit" : "warn",
        message: `CPU ${avg.toFixed(0)}% sustained`,
        observed: formatAgo(history![history!.length - 1].t),
      });
    }
  }
  return out;
}

export function statusLabel(online: boolean | null, severity: Severity): string {
  return online === false ? "DOWN" : online == null ? "UNKNOWN" : severity.toUpperCase();
}

function toFleetNode(s: ServerSummary, g: ServerGroup): FleetNode {
  const alerts = nodeAlerts(s);
  const severity = worst(alerts);
  return {
    uuid: s.uuid,
    name: s.name,
    ip: s.ip,
    kind: s.serverType,
    role: s.description,
    systemId: g.systemId,
    systemName: g.systemName,
    online: s.online,
    severity,
    statusLabel: statusLabel(s.online, severity),
    diskPct: s.storage?.usedPct ?? null,
    diskAbs: s.storage
      ? `${formatBytes(s.storage.usedBytes)} / ${formatBytes(s.storage.totalBytes)}`
      : "no data",
    uptime: formatUptime(s.uptimeSeconds),
    seen: formatAgo(s.lastSeenAt),
    alerts,
    alertLabel: alertLabel(alerts),
  };
}

export function buildFleet(groups: ServerGroup[]): FleetGroup[] {
  return groups.map((g) => ({
    systemId: g.systemId,
    systemName: g.systemName,
    nodes: g.server.map((s) => toFleetNode(s, g)),
  }));
}

/** Every open alert in the fleet, most severe first. */
export function fleetAlerts(fleet: FleetGroup[]): (NodeAlert & { node: FleetNode })[] {
  return fleet
    .flatMap((g) => g.nodes.flatMap((node) => node.alerts.map((a) => ({ ...a, node }))))
    .sort(
      (a, b) =>
        severityRank[b.severity] - severityRank[a.severity] || a.node.name.localeCompare(b.node.name),
    );
}
