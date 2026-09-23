export type RegisterPayload = {
  name: string;
  email: string;
  password: string;
};

export type LoginPayload = {
  email: string;
  password: string;
};

/** Response of POST /api/auth/register */
export type UserResponse = {
  id: number;
  name: string;
  email: string;
  status: string;
  isAdmin: boolean;
};

/** Response of POST /api/auth/login */
export type LoginResponse = {
  accessToken: string;
  expiresInMs: number;
};

/** Shape decoded out of the JWT payload issued by the Spring API. */
export type JwtClaims = {
  iss?: string;
  /** subject — the user's email */
  sub: string;
  /** user id */
  uid: number;
  iat: number;
  exp: number;
};

export type ActionState = {
  error?: string;
  fieldErrors?: Record<string, string>;
  /**
   * Submitted values echoed back. React 19 resets a form once its action
   * settles, so without this the user would retype everything after an error.
   * Passwords are deliberately never echoed.
   */
  values?: { name?: string; email?: string };
};
/* -------------------------------------------------------------------------
   Monitoring API (Spring, /api/v1/**)
   Field-for-field with the responses the backend returns today. Timestamps
   arrive as ISO strings carrying an explicit +07:00 offset, so `new Date(s)`
   is safe and zone-correct.
   ------------------------------------------------------------------------- */

export type ServerType = "DATABASE" | "APP" | "WEB";

/** Host-level storage rollup as of the latest scrape. */
export type StorageSummary = {
  totalBytes: number | null;
  usedBytes: number | null;
  freeBytes: number | null;
  reservedBytes: number | null;
  usedPct: number | null;
  /** Whether the rollup covered every mount the agent attempted. */
  partial: boolean | null;
  recordedAt: string | null;
};

/** One filesystem from the latest scrape. */
export type MountItem = {
  mountPoint: string;
  device: string | null;
  fstype: string | null;
  totalBytes: number | null;
  usedBytes: number | null;
  freeBytes: number | null;
  reservedBytes: number | null;
  usedPct: number | null;
  /** Non-null when the mount could not be measured. Metrics are null then. */
  error: string | null;
};

/** A configured log channel being tailed for this server. */
export type LogChannel = {
  uuid: string;
  channel: string;
  pubPath: string;
  savePath: string;
};

/** `backfill` event of GET /api/logs/{logId}/stream: the end of today's file. */
export type LogStreamBackfill = {
  /** The first `line` event after this one has `seq + 1`. */
  seq: number;
  lines: string[];
};

/** `line` event of GET /api/logs/{logId}/stream. */
export type LogStreamLine = {
  seq: number;
  timestamp: string | null;
  line: string;
};

/** Item in GET /api/v1/servers (grouped by system). */
export type ServerSummary = {
  uuid: string;
  name: string;
  ip: string;
  serverType: ServerType;
  description: string | null;
  /** true reporting, false not reporting, null presence could not be determined. */
  online: boolean | null;
  lastSeenAt: string | null;
  /** Uptime as of the last scrape — stale for an offline server. */
  uptimeSeconds: number | null;
  storage: StorageSummary | null;
  createdAt: string;
};

/** GET /api/v1/servers */
export type ServerGroup = {
  systemId: string;
  systemName: string;
  server: ServerSummary[];
};

/**
 * Latest resource scrape. Frozen for a server that stopped reporting —
 * read it with recordedAt. Every metric may be null on a partial payload.
 */
export type ResourceSnapshot = {
  recordedAt: string | null;
  cpuCount: number | null;
  cpuPct: number | null;
  load1: number | null;
  load5: number | null;
  load15: number | null;
  memTotalBytes: number | null;
  memUsedBytes: number | null;
  memAvailableBytes: number | null;
  memUsedPct: number | null;
  /** True when the agent derived memory use rather than reading it. */
  memEstimated: boolean | null;
  swapTotalBytes: number | null;
  swapUsedBytes: number | null;
  swapUsedPct: number | null;
  netRxBps: number | null;
  netTxBps: number | null;
};

/**
 * Item in GET /api/v1/servers/{uuid}/resources/history — one averaged
 * bucket. Buckets without scrapes are absent, so gaps are real gaps.
 */
export type ResourcePoint = {
  t: string;
  cpuPct: number | null;
  memPct: number | null;
  netRxBps: number | null;
  netTxBps: number | null;
};

/** History plus the window it was requested for (epoch ms), so a chart can place gaps. */
export type ResourceHistory = {
  points: ResourcePoint[];
  from: number;
  to: number;
};

/** GET /api/v1/servers/{uuid} */
export type ServerDetail = Omit<ServerSummary, "storage"> & {
  systemId: string;
  systemName: string;
  resources: ResourceSnapshot | null;
  storage: (StorageSummary & { mounts: MountItem[] }) | null;
  logs: LogChannel[];
};

/** Item in GET /api/v1/systems */
export type SystemItem = {
  uuid: string;
  name: string;
  createdAt: string | null;
};

/** Result of the "Add server" form action. */
export type AddServerState = {
  ok?: boolean;
  error?: string;
  /** Echoed back so a failed submit does not wipe the form. */
  values?: { name?: string; ip?: string; serverType?: string; systemId?: string; description?: string };
};
