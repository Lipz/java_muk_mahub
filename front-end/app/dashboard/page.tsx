import { fetchServerGroups, fetchSystems } from "@/src/lib/servers";
import { buildFleet } from "@/src/lib/health";
import { Empty } from "@/components/monitor/indicators";
import { FleetView } from "@/components/monitor/fleet-view";
import type { ServerGroup, SystemItem } from "@/src/lib/types";

export const metadata = { title: "Server Monitoring" };

/** Scrapes land every few seconds; a cached render would show a dead node as healthy. */
export const dynamic = "force-dynamic";

export default async function NodeMonitorPage() {
  const [groupsResult, systemsResult] = await Promise.allSettled([fetchServerGroups(), fetchSystems()]);

  if (groupsResult.status === "rejected") {
    const err = groupsResult.reason;
    return (
      <div className="p-6">
        <Empty>Could not load the fleet — {err instanceof Error ? err.message : "unknown error"}</Empty>
      </div>
    );
  }

  const groups: ServerGroup[] = groupsResult.value;
  // Systems only populate the Add server picker; the fleet renders without them.
  const systems: SystemItem[] | null = systemsResult.status === "fulfilled" ? systemsResult.value : null;

  return <FleetView fleet={buildFleet(groups)} systems={systems} />;
}
