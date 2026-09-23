/**
 * Presentation helpers for monitoring values.
 *
 * Deliberately pure and framework-free so they can run in server and client
 * components alike, and be unit-tested without a renderer.
 */

/** 1 TiB, 12.4 GiB, 812 MiB … binary units, matching what `df -h` shows. */
export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return "—";
  if (bytes === 0) return "0 B";
  const units = ["B", "KiB", "MiB", "GiB", "TiB", "PiB"];
  const i = Math.min(
    Math.floor(Math.log(Math.abs(bytes)) / Math.log(1024)),
    units.length - 1,
  );
  const value = bytes / 1024 ** i;
  // One decimal below 100 keeps columns narrow without losing resolution.
  const digits = i === 0 ? 0 : value >= 100 ? 0 : 1;
  return `${value.toFixed(digits)} ${units[i]}`;
}

/** 70d 4h, 4h 23m, 12m — coarsest two units that carry meaning. */
export function formatUptime(seconds: number | null | undefined): string {
  if (seconds == null) return "—";
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

/** "4s ago", "12m ago", "3d ago". Null-safe; returns an em dash for null. */
export function formatAgo(iso: string | null | undefined): string {
  if (!iso) return "—";
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "—";
  const secs = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (secs < 60) return `${secs}s ago`;
  const mins = Math.round(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

/** 812 KB/s, 1.4 MB/s — the template's rate format (binary units). */
export function formatRate(bytesPerSec: number | null | undefined): string {
  if (bytesPerSec == null) return "—";
  if (bytesPerSec >= 1024 * 1024) return `${(bytesPerSec / 1024 / 1024).toFixed(1)} MB/s`;
  return `${Math.round(bytesPerSec / 1024)} KB/s`;
}

export function formatPct(pct: number | null | undefined): string {
  return pct == null ? "—" : `${pct.toFixed(1)}%`;
}

/**
 * Severity band for a fill percentage.
 *
 * Thresholds are the conventional disk ones. `/u02` at 93.66% in the live
 * fleet is exactly the case "crit" exists to make impossible to miss.
 */
export type Tone = "ok" | "warn" | "crit" | "muted";

export function usageTone(pct: number | null | undefined): Tone {
  if (pct == null) return "muted";
  if (pct >= 90) return "crit";
  if (pct >= 75) return "warn";
  return "ok";
}
