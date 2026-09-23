/**
 * Shared primitives for the dashboard.
 */

/** Consistent empty state wherever the backend has no data yet. */
export function Empty({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-[5px] border border-dashed border-divider px-3 py-5 text-center text-meta text-[var(--ink-45)]">
      {children}
    </p>
  );
}

/**
 * Line icon for a node kind. The mockup points at app.png / database.png
 * uploads that were never bundled, so these are drawn to the same brief.
 */
export function KindIcon({ kind, className = "" }: { kind: string; className?: string }) {
  const common = {
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.6,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    className,
    role: "img",
    "aria-label": `${kind.toLowerCase()} node`,
  };
  if (kind === "DATABASE")
    return (
      <svg {...common}>
        <ellipse cx="12" cy="5.5" rx="7.5" ry="2.8" />
        <path d="M4.5 5.5v13c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8v-13" />
        <path d="M4.5 12c0 1.5 3.4 2.8 7.5 2.8s7.5-1.3 7.5-2.8" />
      </svg>
    );
  if (kind === "WEB")
    return (
      <svg {...common}>
        <circle cx="12" cy="12" r="8.5" />
        <path d="M3.5 12h17M12 3.5c2.4 2.4 3.6 5.2 3.6 8.5s-1.2 6.1-3.6 8.5c-2.4-2.4-3.6-5.2-3.6-8.5s1.2-6.1 3.6-8.5Z" />
      </svg>
    );
  return (
    <svg {...common}>
      <rect x="3.5" y="4" width="17" height="7" rx="1" />
      <rect x="3.5" y="13" width="17" height="7" rx="1" />
      <path d="M7 7.5h.01M7 16.5h.01M11 7.5h6M11 16.5h6" />
    </svg>
  );
}
