"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, useSyncExternalStore } from "react";
import { usageTone } from "@/src/lib/format";
import { kindLabel, severityRank, type FleetGroup, type FleetNode, type Severity } from "@/src/lib/health";
import type { ServerType, SystemItem } from "@/src/lib/types";
import { Empty, KindIcon } from "./indicators";
import { AddServerDialog } from "./add-server-dialog";

/**
 * Fleet view from the Node Monitor mockup: KPI strip with scope and layout
 * switches, then one collapsible section per system showing nodes as cards
 * (grid) or rows (table).
 *
 * Receives fully-derived data from the server; this component only filters
 * and lays it out, so it never formats times itself (see health.ts).
 */

type Scope = "all" | ServerType;
type Layout = "grid" | "table";

const LAYOUT_KEY = "fleet.layout";

/**
 * Layout preference, remembered per browser. Storage can be unavailable
 * (private mode, blocked site data), so every access is guarded and an
 * in-memory value keeps the toggle working when writes fail.
 */
const layoutStore = (() => {
  let memory: Layout | null = null;
  const listeners = new Set<() => void>();
  return {
    read(): Layout {
      if (memory) return memory;
      try {
        return localStorage.getItem(LAYOUT_KEY) === "table" ? "table" : "grid";
      } catch {
        return "grid";
      }
    },
    write(l: Layout) {
      memory = l;
      try {
        localStorage.setItem(LAYOUT_KEY, l);
      } catch {}
      listeners.forEach((f) => f());
    },
    subscribe(f: () => void) {
      listeners.add(f);
      return () => void listeners.delete(f);
    },
  };
})();

const toneVar: Record<Severity, string> = {
  ok: "var(--color-ok)",
  warn: "var(--color-warn)",
  crit: "var(--color-crit)",
};
const barVar = { ...toneVar, muted: "var(--ink-45)" };
const muted = "color-mix(in srgb, var(--color-text) 73%, transparent)";

/** Presence unknown is a monitor gap, not a node state — shown in grey, not a tone. */
function nodeTone(n: FleetNode) {
  return n.online == null ? "var(--ink-45)" : toneVar[n.severity];
}

export function FleetView({ fleet, systems }: { fleet: FleetGroup[]; systems: SystemItem[] | null }) {
  const router = useRouter();
  const query = (useSearchParams().get("q") ?? "").trim().toLowerCase();
  const [scope, setScope] = useState<Scope>("all");
  const layout = useSyncExternalStore(layoutStore.subscribe, layoutStore.read, (): Layout => "grid");
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  const [addOpen, setAddOpen] = useState(false);

  const all = fleet.flatMap((g) => g.nodes);
  const inScope = all.filter((n) => scope === "all" || n.kind === scope);
  const reporting = inScope.filter((n) => n.diskPct != null);

  const kpis = [
    { label: "Nodes up", value: `${inScope.filter((n) => n.online === true).length}/${inScope.length}`, tone: "var(--color-text)" },
    { label: "Alerting", value: String(inScope.filter((n) => n.severity !== "ok").length), tone: toneVar.crit },
    { label: "Open alerts", value: String(inScope.reduce((a, n) => a + n.alerts.length, 0)), tone: toneVar.warn },
    {
      label: "Avg disk",
      value: reporting.length
        ? `${Math.round(reporting.reduce((a, n) => a + n.diskPct!, 0) / reporting.length)}%`
        : "—",
      tone: "var(--color-text)",
    },
    { label: "Disk > 85%", value: String(reporting.filter((n) => n.diskPct! > 85).length), tone: "var(--color-text)" },
  ];

  const kinds: ServerType[] = ["APP", "WEB", "DATABASE"];
  const scopes: { value: Scope; label: string }[] = [
    { value: "all", label: `All ${all.length}` },
    ...kinds
      .map((k) => ({ value: k as Scope, label: `${kindLabel[k]} ${all.filter((n) => n.kind === k).length}`, n: all.filter((n) => n.kind === k).length }))
      .filter((s) => s.n > 0),
  ];

  const matches = (n: FleetNode) =>
    !query || `${n.name} ${n.ip} ${n.role ?? ""} ${n.systemName}`.toLowerCase().includes(query);

  const filtering = scope !== "all" || !!query;
  const groups = fleet
    .map((g) => {
      const nodes = g.nodes.filter((n) => (scope === "all" || n.kind === scope) && matches(n));
      const worst = nodes.reduce<Severity>((w, n) => (severityRank[n.severity] > severityRank[w] ? n.severity : w), "ok");
      return { ...g, nodes, worst };
    })
    // An empty system is worth showing only when nothing is filtering it out.
    .filter((g) => g.nodes.length > 0 || !filtering)
    .sort((a, b) => severityRank[b.worst] - severityRank[a.worst]);

  const toggle = (id: string) => setCollapsed((c) => ({ ...c, [id]: !c[id] }));

  return (
    <div>
      <div className="flex flex-wrap items-center gap-x-3.5 gap-y-1.5 border-b border-divider bg-[color-mix(in_srgb,var(--color-chrome)_60%,var(--color-bg))] px-6 py-[3px]">
        {kpis.map((k) => (
          <div key={k.label} className="flex flex-none items-center gap-1.5">
            <span className="size-1 flex-none rounded-full" style={{ background: k.tone }} />
            <span className="text-[10px] leading-none font-semibold">{k.value}</span>
            <span className="text-[10px]" style={{ color: muted }}>
              {k.label}
            </span>
          </div>
        ))}

        <div className="ml-auto flex flex-wrap items-center justify-end gap-2.5">
          <Segmented options={scopes} value={scope} onChange={setScope} />
          <div className="border-l border-divider pl-2">
            <Segmented
              options={[
                { value: "grid", label: "Grid" },
                { value: "table", label: "Table" },
              ]}
              value={layout}
              onChange={layoutStore.write}
            />
          </div>
          <button
            type="button"
            onClick={() => setAddOpen(true)}
            className="btn btn-primary flex-none rounded-[4px] px-3 py-1 text-[10px] whitespace-nowrap"
          >
            Add server
          </button>
        </div>
      </div>

      {groups.length === 0 ? (
        <div className="p-6">
          <Empty>{fleet.length === 0 ? "No systems registered yet." : "No servers match this filter."}</Empty>
        </div>
      ) : layout === "grid" ? (
        <div className="flex flex-col gap-[26px] p-6">
          {groups.map((g) => (
            <section key={g.systemId}>
              <GroupHeaderGrid group={g} collapsed={!!collapsed[g.systemId]} onToggle={() => toggle(g.systemId)} />
              {collapsed[g.systemId] ? null : g.nodes.length === 0 ? (
                <Empty>No nodes in this system.</Empty>
              ) : (
                <div className="grid grid-cols-[repeat(auto-fill,minmax(186px,1fr))] items-stretch gap-3">
                  {g.nodes.map((n) => (
                    <NodeCard key={n.uuid} node={n} />
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>
      ) : (
        <div className="flex flex-col gap-6 px-6 pt-5 pb-7">
          {groups.map((g) => (
            <section key={g.systemId}>
              <GroupHeaderTable group={g} collapsed={!!collapsed[g.systemId]} onToggle={() => toggle(g.systemId)} />
              {collapsed[g.systemId] ? null : g.nodes.length === 0 ? (
                <Empty>No nodes in this system.</Empty>
              ) : (
                <div className="overflow-x-auto">
                  <NodeTable nodes={g.nodes} onOpen={(uuid) => router.push(`/dashboard/nodes/${uuid}`)} />
                </div>
              )}
            </section>
          ))}
        </div>
      )}

      {addOpen ? <AddServerDialog systems={systems} onClose={() => setAddOpen(false)} /> : null}
    </div>
  );
}

function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <div className="flex gap-[2px]" role="group">
      {options.map((o) => {
        const on = o.value === value;
        return (
          <button
            key={o.value}
            type="button"
            aria-pressed={on}
            onClick={() => onChange(o.value)}
            className={`flex-none cursor-pointer rounded-full px-2.5 py-[3px] text-[10px] whitespace-nowrap ${
              on ? "bg-accent text-white" : "hover:bg-[color-mix(in_srgb,var(--color-accent)_10%,transparent)]"
            }`}
            style={on ? undefined : { color: "color-mix(in srgb, var(--color-text) 62%, transparent)" }}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

type ViewGroup = FleetGroup & { worst: Severity };

function groupSummary(g: ViewGroup) {
  const count = (k: ServerType) => g.nodes.filter((n) => n.kind === k).length;
  const bad = g.nodes.filter((n) => n.severity !== "ok").length;
  const parts = [`${g.nodes.length} ${g.nodes.length === 1 ? "node" : "nodes"}`];
  for (const k of ["APP", "WEB", "DATABASE"] as const) if (count(k)) parts.push(`${count(k)} ${kindLabel[k].toLowerCase()}`);
  if (bad) parts.push(`${bad} alerting`);
  return parts.join(" · ");
}

function GroupHeaderGrid({ group, collapsed, onToggle }: { group: ViewGroup; collapsed: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={!collapsed}
      className="mb-2.5 flex w-full cursor-pointer items-center gap-[9px] rounded-[5px] bg-[color-mix(in_srgb,var(--color-chrome)_70%,var(--color-bg))] px-[11px] py-[5px] text-left hover:bg-[color-mix(in_srgb,var(--color-accent)_10%,transparent)]"
    >
      <span className="w-4 flex-none text-center text-[16px] leading-none" style={{ color: "color-mix(in srgb, var(--color-text) 70%, transparent)" }}>
        {collapsed ? "+" : "–"}
      </span>
      <span className="font-heading text-[13px] font-semibold whitespace-nowrap">{group.systemName}</span>
      <span className="flex-1" />
      <span className="text-[11px] whitespace-nowrap" style={{ color: muted }}>
        {groupSummary(group)}
      </span>
    </button>
  );
}

function GroupHeaderTable({ group, collapsed, onToggle }: { group: ViewGroup; collapsed: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-expanded={!collapsed}
      className="mb-1.5 flex w-full cursor-pointer items-center gap-[11px] px-1.5 py-[5px] text-left hover:bg-[color-mix(in_srgb,var(--color-accent)_7%,transparent)]"
    >
      <span
        className="flex size-[26px] flex-none items-center justify-center border border-divider text-[14px] leading-none"
        style={{ color: "color-mix(in srgb, var(--color-text) 70%, transparent)" }}
      >
        {collapsed ? "+" : "–"}
      </span>
      <span className="size-2 flex-none" style={{ background: toneVar[group.worst] }} />
      <span className="font-heading text-[15px] font-semibold">{group.systemName}</span>
      <span className="text-[12px]" style={{ color: muted }}>
        {groupSummary(group)}
      </span>
    </button>
  );
}

function NodeCard({ node: n }: { node: FleetNode }) {
  const tone = nodeTone(n);
  const disk = n.diskPct == null ? null : Math.min(100, Math.max(0, n.diskPct));
  return (
    <Link
      href={`/dashboard/nodes/${n.uuid}`}
      className="flex flex-col overflow-hidden rounded-[5px] border border-divider bg-white text-ink no-underline shadow-[var(--shadow-sm)] transition-shadow hover:border-accent-300 hover:shadow-[var(--shadow-md)]"
    >
      <div className="flex items-start gap-2 px-[11px] pt-2.5">
        <div className="min-w-0 flex-1">
          <div className="font-heading truncate text-[14px] leading-[1.2] font-semibold">{n.name}</div>
          <div className="mono mt-[2px] truncate text-[10px]" style={{ color: muted }}>
            {n.ip}
          </div>
        </div>
        <div
          className="flex flex-none items-center gap-1.5 rounded-full px-[7px] py-[2px]"
          style={{ background: `color-mix(in srgb, ${tone} 7%, var(--color-bg))` }}
        >
          <span className="size-1.5 flex-none rounded-full" style={{ background: tone }} />
          <span
            className="text-[10px] font-semibold whitespace-nowrap"
            style={{ color: n.severity === "ok" && n.online !== false ? "color-mix(in srgb, var(--color-text) 62%, transparent)" : tone }}
          >
            {n.statusLabel}
          </span>
        </div>
      </div>

      <div className="flex items-center gap-1.5 px-[11px] pt-[7px]">
        <KindIcon kind={n.kind} className="size-[13px] flex-none opacity-50" />
        <span className="text-[10.5px] whitespace-nowrap" style={{ color: muted }}>
          {kindLabel[n.kind]} node
        </span>
        <span className="h-[11px] w-px flex-none" style={{ background: "color-mix(in srgb, var(--color-text) 16%, transparent)" }} />
        <span className="min-w-0 flex-1 truncate text-[10.5px]" style={{ color: muted }}>
          {n.role ?? "—"}
        </span>
      </div>

      <div className="flex flex-col gap-[3px] px-[11px] pt-[9px]">
        <Leader label="Disk" value={n.diskAbs} />
        <Leader label="Uptime" value={n.online === false ? `${n.uptime} · seen ${n.seen}` : n.uptime} />
      </div>

      <div className="mt-2.5 flex items-center gap-[9px] border-t border-divider bg-[color-mix(in_srgb,var(--color-chrome)_45%,#ffffff)] px-[11px] pt-[7px] pb-[9px]">
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[color-mix(in_srgb,var(--color-text)_10%,transparent)]">
          {disk != null ? (
            <div className="h-full rounded-full" style={{ width: `${disk}%`, background: barVar[usageTone(disk)] }} />
          ) : null}
        </div>
        <span className="mono flex-none text-[10px]" style={{ color: muted }}>
          {disk == null ? "—" : `${disk.toFixed(0)}%`}
        </span>
        <span className="flex-none text-[10px] whitespace-nowrap" style={{ color: muted }}>
          {n.alertLabel}
        </span>
      </div>
    </Link>
  );
}

function Leader({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-1.5">
      <span className="flex-none text-[10px]" style={{ color: muted }}>
        {label}
      </span>
      <span className="h-0 flex-1 border-b border-dotted border-[color-mix(in_srgb,var(--color-text)_16%,transparent)]" />
      <span className="mono flex-none text-[10.5px]">{value}</span>
    </div>
  );
}

function NodeTable({ nodes, onOpen }: { nodes: FleetNode[]; onOpen: (uuid: string) => void }) {
  const faint = "color-mix(in srgb, var(--color-text) 48%, transparent)";
  return (
    <table className="table min-w-[760px]">
      <thead>
        <tr>
          <th className="w-[26px]" />
          <th className="w-[180px]">Node</th>
          <th className="w-[220px]">Role</th>
          <th className="w-[150px]">Disk usage</th>
          <th className="w-[100px]">Status</th>
          <th className="w-[110px]">Alerts</th>
          <th className="w-[90px]">Uptime</th>
        </tr>
      </thead>
      <tbody>
        {nodes.map((n) => {
          const disk = n.diskPct == null ? null : Math.min(100, Math.max(0, n.diskPct));
          return (
            <tr key={n.uuid} className="rowlink cursor-pointer" onClick={() => onOpen(n.uuid)}>
              <td className="pl-0">
                <span className="block size-2" style={{ background: nodeTone(n) }} />
              </td>
              <td>
                {/* A real link keeps the row keyboard- and middle-click-reachable. */}
                <Link href={`/dashboard/nodes/${n.uuid}`} className="mono block text-[13px] whitespace-nowrap text-ink no-underline" onClick={(e) => e.stopPropagation()}>
                  {n.name}
                </Link>
                <div className="text-[10.5px] whitespace-nowrap" style={{ color: faint }}>
                  {n.ip}
                </div>
              </td>
              <td>
                <div className="flex items-center gap-[9px] whitespace-nowrap">
                  <KindIcon kind={n.kind} className="size-6 flex-none opacity-75" />
                  <span className="text-[12px]">{n.role ?? `${kindLabel[n.kind]} node`}</span>
                </div>
              </td>
              <td>
                <div className="flex items-center gap-[9px]">
                  <div className="h-[7px] flex-1 bg-[var(--ink-08)]">
                    {disk != null ? <div className="h-full" style={{ width: `${disk}%`, background: barVar[usageTone(disk)] }} /> : null}
                  </div>
                  <div className="mono w-[34px] text-right text-[12px]">{disk == null ? "—" : `${disk.toFixed(0)}%`}</div>
                </div>
                <div className="mono mt-[2px] text-[10.5px]" style={{ color: faint }}>
                  {n.diskAbs}
                </div>
              </td>
              <td className="text-[12px]">
                <span style={{ color: n.severity === "ok" && n.online !== false ? undefined : nodeTone(n) }}>{n.statusLabel}</span>
                {n.online === false ? (
                  <div className="text-[10.5px]" style={{ color: faint }}>
                    seen {n.seen}
                  </div>
                ) : null}
              </td>
              <td className="text-[12px]">{n.alertLabel}</td>
              <td className="mono text-[12px]">{n.uptime}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
