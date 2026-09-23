import Link from "next/link";
import { fetchServerGroups } from "@/src/lib/servers";
import { buildFleet, fleetAlerts, type Severity } from "@/src/lib/health";
import { Empty } from "@/components/monitor/indicators";

export const metadata = { title: "Alerts · Server Monitoring" };
export const dynamic = "force-dynamic";

const toneVar: Record<Severity, string> = {
  ok: "var(--color-ok)",
  warn: "var(--color-warn)",
  crit: "var(--color-crit)",
};
const sevLabel: Record<Severity, string> = { ok: "OK", warn: "WARNING", crit: "CRITICAL" };

export default async function AlertsPage(props: PageProps<"/dashboard/alerts">) {
  const q = (await props.searchParams).q;
  const query = (typeof q === "string" ? q : "").trim().toLowerCase();

  let alerts;
  try {
    alerts = fleetAlerts(buildFleet(await fetchServerGroups()));
  } catch (err) {
    return (
      <div className="p-6">
        <Empty>Could not load alerts — {err instanceof Error ? err.message : "unknown error"}</Empty>
      </div>
    );
  }

  const crit = alerts.filter((a) => a.severity === "crit").length;
  const shown = query
    ? alerts.filter((a) => `${a.node.name} ${a.node.ip} ${a.node.systemName} ${a.message}`.toLowerCase().includes(query))
    : alerts;

  return (
    <div className="p-6">
      <div className="mb-3 flex items-baseline gap-3">
        <h3 className="m-0">Open alerts</h3>
        <span className="mono text-[11.5px] text-[color-mix(in_srgb,var(--color-text)_50%,transparent)]">
          {alerts.length} open · {crit} critical · click a row to open the node
        </span>
      </div>

      {shown.length === 0 ? (
        <Empty>{alerts.length === 0 ? "All checks passing across the fleet." : "No alerts match this search."}</Empty>
      ) : (
        <div className="overflow-x-auto">
          <table className="table min-w-[640px]">
            <thead>
              <tr>
                <th className="w-[26px]" />
                <th className="w-[120px]">Severity</th>
                <th className="w-[150px]">Node</th>
                <th>Alert</th>
                <th className="w-[160px]">System</th>
                <th className="w-[110px]">Observed</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((a, i) => (
                <tr key={`${a.node.uuid}-${i}`} className="rowlink relative">
                  <td className="pl-0">
                    <span className="block size-2" style={{ background: toneVar[a.severity] }} />
                  </td>
                  <td>
                    <span className="mono px-1.5 py-[3px] text-[9.5px] tracking-[.09em] text-[#f2f2f3]" style={{ background: toneVar[a.severity] }}>
                      {sevLabel[a.severity]}
                    </span>
                  </td>
                  <td className="mono text-[12.5px]">
                    <Link href={`/dashboard/nodes/${a.node.uuid}`} className="text-ink no-underline after:absolute after:inset-0">
                      {a.node.name}
                    </Link>
                  </td>
                  <td className="text-[13px]">{a.message}</td>
                  <td className="text-[12px]">{a.node.systemName}</td>
                  <td className="mono text-[12px]">{a.observed}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
