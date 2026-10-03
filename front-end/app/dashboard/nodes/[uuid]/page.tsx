import { notFound } from "next/navigation";
import { fetchResourceHistory, fetchServer } from "@/src/lib/servers";
import { ApiError } from "@/src/lib/api";
import { Empty } from "@/components/monitor/indicators";
import { APP_TABS, AppNodeView, type AppTab } from "@/components/monitor/node/app-node-view";
import { DB_TABS, DbNodeView, type DbTab } from "@/components/monitor/node/db-node-view";
import type { ResourceHistory, ServerDetail } from "@/src/lib/types";

export const dynamic = "force-dynamic";

/** The hub's shortest history window, and long enough for the CPU alert. */
const HISTORY_MINUTES = 5;

/**
 * Node detail. One route, two templates chosen by server type: databases get
 * the DB view (sessions, replication, backups, alert log); App and Web nodes
 * share the App/Web view (overview, log tail).
 */
export default async function NodePage(props: PageProps<"/dashboard/nodes/[uuid]">) {
  const [{ uuid }, sp] = await Promise.all([props.params, props.searchParams]);
  const file = typeof sp.file === "string" ? sp.file : undefined;

  // History feeds the CPU alert (3-minute average) and seeds the overview's
  // live tiles, which show its last minute, until the stream's backfill arrives. Fetched alongside the node rather than after it,
  // and a failure degrades the sparklines, not the page.
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
    return <DbNodeView node={node} tab={tab as DbTab} file={file} history={history} />;
  }

  const tab = APP_TABS.find((t) => t.id === sp.tab)?.id ?? "overview";
  return <AppNodeView node={node} tab={tab as AppTab} file={file} history={history} />;
}
