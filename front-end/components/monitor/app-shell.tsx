"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { logoutAction } from "@/src/lib/actions";

/**
 * Dashboard chrome from the Node Monitor mockup: an accent-700 command bar
 * (nav toggle, product name, pill search, account pill) over a white tab strip.
 *
 * Search lives in the URL (`?q=`) so the fleet view — a separate component —
 * can read it, and so a filtered view survives reload and can be shared.
 */
export function AppShell({
  user,
  initials,
  alertCount,
  children,
}: {
  user: string;
  initials: string;
  alertCount: number | null;
  children: React.ReactNode;
}) {
  const [navOpen, setNavOpen] = useState(true);
  const [accountOpen, setAccountOpen] = useState(false);
  const pathname = usePathname();

  const tabs = [
    { href: "/dashboard", label: "Server", active: pathname === "/dashboard" || pathname.startsWith("/dashboard/nodes") },
    {
      href: "/dashboard/alerts",
      label: alertCount == null ? "Alerts" : `Alerts · ${alertCount}`,
      active: pathname.startsWith("/dashboard/alerts"),
    },
  ];

  return (
    <div className="flex min-h-screen flex-col font-body text-ink">
      <header className="z-[5] flex flex-wrap items-center gap-x-3.5 gap-y-2 bg-accent-700 px-4 py-1 text-white">
        <div className="flex flex-none items-center gap-2.5">
          <button
            type="button"
            onClick={() => setNavOpen((o) => !o)}
            aria-label="Toggle navigation"
            aria-expanded={navOpen}
            className="flex size-[26px] flex-none cursor-pointer items-center justify-center rounded-[4px] hover:bg-white/15"
          >
            <span className="grid grid-cols-[repeat(3,2.5px)] grid-rows-[repeat(3,2.5px)] gap-[2px]">
              {Array.from({ length: 9 }, (_, i) => (
                <span key={i} className="bg-white/85" />
              ))}
            </span>
          </button>
          <Link
            href="/dashboard"
            className="font-heading text-[14px] font-semibold whitespace-nowrap text-white no-underline"
          >
            Server Monitoring
          </Link>
        </div>

        <Suspense fallback={<div className="max-w-[480px] min-w-[160px] flex-[1_1_240px]" />}>
          <SearchBox />
        </Suspense>

        <div className="relative ml-auto flex flex-none items-center gap-1.5">
          <button
            type="button"
            onClick={() => setAccountOpen(true)}
            aria-haspopup="dialog"
            aria-label="Account"
            className={`flex cursor-pointer items-center gap-2 rounded-full py-[2px] pr-[3px] pl-[11px] hover:bg-white/15 ${accountOpen ? "bg-white/20" : ""}`}
          >
            <span className="text-[12px] leading-[1.25] font-semibold whitespace-nowrap text-white">{user}</span>
            <span className="font-heading flex size-[26px] flex-none items-center justify-center rounded-full bg-white text-[11px] font-semibold tracking-[.02em] text-accent-700">
              {initials}
            </span>
          </button>
        </div>
      </header>

      {navOpen ? (
        <nav className="sticky top-0 z-[4] flex gap-1 border-b border-divider bg-white px-[18px]">
          {tabs.map((t) => (
            <Link
              key={t.href}
              href={t.href}
              aria-current={t.active ? "page" : undefined}
              className={`border-b-2 px-[13px] py-[7px] text-[13px] font-semibold whitespace-nowrap no-underline hover:bg-[color-mix(in_srgb,var(--color-accent)_9%,transparent)] ${
                t.active ? "border-accent-700 text-accent-700" : "border-transparent text-[color-mix(in_srgb,var(--color-text)_62%,transparent)]"
              }`}
            >
              {t.label}
            </Link>
          ))}
        </nav>
      ) : null}

      <main className="flex-1">{children}</main>

      {accountOpen ? (
        <AccountMenu user={user} initials={initials} onClose={() => setAccountOpen(false)} />
      ) : null}
    </div>
  );
}

function SearchBox() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const urlQuery = params.get("q") ?? "";
  const [query, setQuery] = useState(urlQuery);
  const [seenUrlQuery, setSeenUrlQuery] = useState(urlQuery);

  // Clear the box when navigation drops `?q=` (e.g. clicking the Server tab).
  // Non-empty URL values are ignored: while typing, replace() lands a step
  // behind the input and would otherwise eat keystrokes.
  if (urlQuery !== seenUrlQuery) {
    setSeenUrlQuery(urlQuery);
    if (!urlQuery) setQuery("");
  }

  function apply(next: string) {
    setQuery(next);
    // Search filters the fleet; from any other page, typing takes you there.
    const base = pathname === "/dashboard/alerts" ? pathname : "/dashboard";
    const qs = next.trim() ? `?q=${encodeURIComponent(next)}` : "";
    router.replace(`${base}${qs}`, { scroll: false });
  }

  return (
    <div className="flex max-w-[480px] min-w-[160px] flex-[1_1_240px] items-center gap-1.5 rounded-full bg-white/16 px-2.5">
      <svg viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,.8)" strokeWidth="1.5" className="size-3 flex-none" aria-hidden>
        <circle cx="11" cy="11" r="7" />
        <path d="M16.5 16.5 21 21" />
      </svg>
      <input
        type="search"
        value={query}
        onChange={(e) => apply(e.target.value)}
        placeholder="Search servers, IP, role"
        aria-label="Search servers"
        className="h-9 min-w-0 flex-1 border-0 bg-transparent p-0 text-[12px] leading-[22px] text-white shadow-none outline-none placeholder:text-white/60 focus-visible:outline-none [&::-webkit-search-cancel-button]:hidden"
      />
      {query ? (
        <button
          type="button"
          onClick={() => apply("")}
          aria-label="Clear search"
          className="flex-none cursor-pointer px-1 py-0.5 text-[14px] leading-none text-white/80"
        >
          ×
        </button>
      ) : null}
    </div>
  );
}

function AccountMenu({ user, initials, onClose }: { user: string; initials: string; onClose: () => void }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="fixed inset-0 z-[45] flex items-start justify-end px-4 pt-11" onClick={onClose}>
      <div
        role="dialog"
        aria-label="Account"
        onClick={(e) => e.stopPropagation()}
        className="flex w-[228px] flex-col rounded-[4px] border border-divider bg-white p-1.5 shadow-[var(--shadow-lg)]"
      >
        <div className="flex items-center gap-[9px] px-2 pt-1.5 pb-[9px]">
          <span className="font-heading flex size-8 flex-none items-center justify-center rounded-full bg-accent-700 text-[12px] font-semibold text-white">
            {initials}
          </span>
          <div className="min-w-0 flex-1">
            <div className="truncate text-[13px] leading-[1.25] font-semibold">{user.split("@")[0]}</div>
            <div className="truncate text-[11px] leading-[1.25] text-[color-mix(in_srgb,var(--color-text)_58%,transparent)]">
              {user}
            </div>
          </div>
        </div>
        <div className="flex flex-col border-t border-divider pt-[5px]">
          <form action={logoutAction}>
            <button
              type="submit"
              className="flex w-full cursor-pointer items-center gap-[9px] rounded-[4px] px-2 py-1.5 text-left text-[13px] hover:bg-[color-mix(in_srgb,var(--color-accent)_10%,transparent)]"
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="color-mix(in srgb,var(--color-text) 58%,transparent)"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="size-[15px] flex-none"
                aria-hidden
              >
                <path d="M15 17l5-5-5-5M20 12H9M13 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h7" />
              </svg>
              <span className="flex-1 whitespace-nowrap">Sign out</span>
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

/**
 * Re-fetches server components on an interval so the fleet stays live
 * without a manual reload. Pauses while the tab is hidden.
 */
export function AutoRefresh({ everyMs = 30_000 }: { everyMs?: number }) {
  const router = useRouter();
  useEffect(() => {
    const id = setInterval(() => {
      if (document.visibilityState === "visible") router.refresh();
    }, everyMs);
    return () => clearInterval(id);
  }, [router, everyMs]);
  return null;
}
