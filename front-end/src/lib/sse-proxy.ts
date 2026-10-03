import { API_BASE_URL } from "@/src/lib/api";
import { getToken } from "@/src/lib/session";

/**
 * Same-origin proxy for a Spring SSE stream. The JWT lives in an httpOnly
 * cookie and the browser cannot set an Authorization header on a stream it
 * opens, so it opens a Next route and we forward the stream byte for byte.
 *
 * 401, 403 and 404 keep their meaning so the client can stop retrying; any
 * other failure is a 502. Only a dropped 200 stream is worth reconnecting to.
 */
export async function proxyEventStream(request: Request, upstreamPath: string): Promise<Response> {
  const token = await getToken();
  if (!token) return new Response("Unauthorized", { status: 401 });

  let upstream: Response;
  try {
    upstream = await fetch(`${API_BASE_URL}${upstreamPath}`, {
      cache: "no-store",
      headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
      // Browser gone -> abort the upstream request, so Spring drops the stream
      signal: request.signal,
    });
  } catch {
    return new Response("Cannot reach the API", { status: 502 });
  }

  if (!upstream.ok || !upstream.body) {
    await upstream.body?.cancel();
    // Never pass Spring's error body through
    const status = [401, 403, 404].includes(upstream.status) ? upstream.status : 502;
    return new Response(null, { status });
  }

  return new Response(upstream.body, {
    headers: {
      "Content-Type": "text/event-stream; charset=utf-8",
      // no-transform also keeps Next's gzip from buffering the stream
      "Cache-Control": "no-cache, no-transform",
      // Stop nginx-style reverse proxies from buffering it too
      "X-Accel-Buffering": "no",
    },
  });
}
