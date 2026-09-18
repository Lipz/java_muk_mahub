import { redirect } from "next/navigation";
import { getSession } from "@/src/lib/session";
import { logoutAction } from "@/src/lib/actions";

export const metadata = { title: "Dashboard" };

export default async function DashboardPage() {
  const session = await getSession();
  console.log(session || `session not found`);
  if (!session) redirect("/login");

  const expires = new Date(session.exp * 1000);

  return (
    <main className="min-h-screen bg-neutral-50 px-4 py-12 dark:bg-neutral-950">
      <div className="mx-auto w-full max-w-xl">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold tracking-tight">Dashboard</h1>
          <form action={logoutAction}>
            <button
              type="submit"
              className="rounded-lg border border-neutral-300 px-3 py-1.5 text-sm transition hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-800"
            >
              Sign out
            </button>
          </form>
        </div>

        <p className="mt-2 text-sm text-neutral-500">
          You are signed in. This page is server-rendered and reads the session
          from an httpOnly cookie.
        </p>

        <dl className="mt-6 divide-y divide-neutral-200 rounded-2xl border border-neutral-200 bg-white text-sm dark:divide-neutral-800 dark:border-neutral-800 dark:bg-neutral-900">
          <Row label="Email" value={session.sub} />
          <Row label="User ID" value={String(session.uid)} />
          {session.iss ? <Row label="Issuer" value={session.iss} /> : null}
          <Row label="Token expires" value={expires.toLocaleString()} />
        </dl>
      </div>
    </main>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 px-4 py-3">
      <dt className="text-neutral-500">{label}</dt>
      <dd className="font-medium break-all">{value}</dd>
    </div>
  );
}