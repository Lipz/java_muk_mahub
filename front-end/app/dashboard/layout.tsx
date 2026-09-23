import { redirect } from "next/navigation";
import { getSession } from "@/src/lib/session";
import { fetchServerGroups } from "@/src/lib/servers";
import { buildFleet, fleetAlerts } from "@/src/lib/health";
import { AppShell, AutoRefresh } from "@/components/monitor/app-shell";

/**
 * Dashboard chrome. The session check lives here rather than in each page so
 * a new route cannot accidentally ship unauthenticated.
 */
export default async function DashboardLayout(props: LayoutProps<"/dashboard">) {
  const session = await getSession();
  if (!session) redirect("/login");

  const user = session.sub ?? "";
  const initials = user
    .split("@")[0]
    .split(/[._-]/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join("") || "?";

  // Only feeds the count on the Alerts tab; the page reports load errors
  // itself, so a failure here just drops the number.
  let alertCount: number | null = null;
  try {
    alertCount = fleetAlerts(buildFleet(await fetchServerGroups())).length;
  } catch {
    alertCount = null;
  }

  return (
    <AppShell user={user} initials={initials} alertCount={alertCount}>
      {props.children}
      <AutoRefresh />
    </AppShell>
  );
}
