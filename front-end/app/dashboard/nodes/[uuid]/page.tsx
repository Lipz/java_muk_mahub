import { notFound } from "next/navigation";
import { fetchResourceHistory, fetchServer } from "@/src/lib/servers";
import { ApiError } from "@/src/lib/api";
import { Empty } from "@/components/monitor/indicators";
import { APP_TABS, AppNodeView, HISTORY_MINUTES, type AppTab } from "@/components/monitor/node/app-node-view";
import { DB_TABS, DbNodeView, type DbTab } from "@/components/monitor/node/db-node-view";
import type { ResourceHistory, ServerDetail } from "@/src/lib/types";

export const dynamic = "force-dynamic";

/**
 * Node detail. One route, two templates chosen by server type: databases get
 * the DB view (sessions, replication, backups, alert log); App and Web nodes
 * share the App/Web view (overview, log tail).
 */
export default async function NodePage(props: PageProps<"/dashboard/nodes/[uuid]">) {
  const [{ uuid }, sp] = await Promise.all([props.params, props.searchParams]);
  const file = typeof sp.file === "string" ? sp.file : undefined;

  // History only feeds the App/Web overview tiles, but the node type is not
  // known until the node loads — fetch both at once rather than in series,
  // and let a history failure degrade the sparklines, not the page.
  const wantsHistory = sp.tab == null || sp.tab === "overview";
  const [nodeResult, history] = await Promise.all([
    fetchServer(uuid).then(
      (node) => ({ node }),
      (err: unknown) => ({ err }),
    ),
    wantsHistory ? fetchResourceHistory(uuid, HISTORY_MINUTES).catch((): ResourceHistory | null => null) : null,
  ]);

  if ("err" in nodeResult) {
    const err = nodeResult.err;
    if (err instanceof ApiError && err.status === 404) notFound();
    return (
      <div className="px-6 py-4">
        <Empty>Could not load node — {err instanceof Error ? err.message : "unknown error"}</Empty>
      </div>
    );
  }
  const node: ServerDetail = nodeResult.node;

  if (node.serverType === "DATABASE") {
    const tab = DB_TABS.find((t) => t.id === sp.tab)?.id ?? "overview";
    return <DbNodeView node={node} tab={tab as DbTab} file={file} />;
  }

  const tab = APP_TABS.find((t) => t.id === sp.tab)?.id ?? "overview";
  return <AppNodeView node={node} tab={tab as AppTab} file={file} history={history} />;
}
