import { proxyEventStream } from "@/src/lib/sse-proxy";

/** Live CPU / memory / network scrapes of one node, proxied from the hub (see proxyEventStream). */
export async function GET(request: Request, ctx: RouteContext<"/api/servers/[uuid]/resources/stream">) {
  const { uuid } = await ctx.params;
  return proxyEventStream(request, `/api/v1/servers/${encodeURIComponent(uuid)}/resources/stream`);
}
