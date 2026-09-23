import "server-only";
import { cache } from "react";
import { apiRequest } from "./api";
import { getToken } from "./session";
import type { ResourceHistory, ResourcePoint, ServerDetail, ServerGroup, SystemItem } from "./types";

/**
 * Reads for the monitoring endpoints.
 *
 * Every call is authenticated with the session JWT and uncached
 * (apiRequest sets cache: "no-store"): this data changes every few seconds,
 * so a stale render would show a dead node as healthy.
 */

class NotAuthenticated extends Error {}

async function authed<T>(path: string): Promise<T> {
  const token = await getToken();
  if (!token) throw new NotAuthenticated("No valid session");
  return apiRequest<T>(path, { token });
}

/**
 * Wrapped in React `cache` so the dashboard layout (alert count in the tab)
 * and the page share one request per render instead of hitting the API twice.
 */
export const fetchServerGroups = cache(async (): Promise<ServerGroup[]> => {
  return authed<ServerGroup[]>("/api/v1/servers");
});

export async function fetchServer(uuid: string): Promise<ServerDetail> {
  return authed<ServerDetail>(`/api/v1/servers/${encodeURIComponent(uuid)}`);
}

/** Averaged resource history for the node page sparklines (30 buckets). */
export async function fetchResourceHistory(uuid: string, minutes = 60): Promise<ResourceHistory> {
  const to = Date.now();
  const points = await authed<ResourcePoint[]>(
    `/api/v1/servers/${encodeURIComponent(uuid)}/resources/history?minutes=${minutes}`,
  );
  return { points, from: to - minutes * 60_000, to };
}

export async function fetchSystems(): Promise<SystemItem[]> {
  return authed<SystemItem[]>("/api/v1/systems");
}
