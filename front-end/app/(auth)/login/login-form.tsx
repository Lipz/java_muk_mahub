"use client";

import { useSearchParams } from "next/navigation";
import { useActionState } from "react";
import { loginAction } from "@/src/lib/actions";
import { Field, FormError, SubmitButton } from "@/components/form";
import type { ActionState } from "@/src/lib/types";

export default function LoginForm() {
  const [state, formAction] = useActionState<ActionState, FormData>(
    loginAction,
    {},
  );
  // Set by the proxy when it bounces a signed-out visitor off a private page.
  const from = useSearchParams().get("from") ?? "";

  return (
    <div className="grid w-full max-w-4xl overflow-hidden rounded-2xl bg-white shadow-xl ring-1 ring-black/5 lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-[#f3f6fb] p-10 text-[#1b1b1f] lg:flex">
        <div className="text-lg font-semibold tracking-tight text-[oklch(0.405_0.112_254)]">
          Servers Monitoring
        </div>
        <div>
          <h1 className="max-w-xs text-4xl leading-tight font-semibold tracking-tight">
            Server &amp; Database Nodes Monitoring
          </h1>
          <p className="mt-3.5 max-w-xs text-sm leading-relaxed text-neutral-500">
            checking server availability, daily backup and database status.
          </p>
        </div>
        <div className="text-xs tracking-wide text-neutral-400">
          GDCE-ITD Database Administrator @2026
        </div>
      </div>

      <div className="flex flex-col justify-center p-8 sm:p-10">
        <div className="mb-6">
          <div className="text-xs font-semibold tracking-wide text-[oklch(0.405_0.112_254)] uppercase">
            Sign in
          </div>
          <h3 className="mt-1 text-xl font-semibold tracking-tight text-neutral-900">
            Database Operation Console
          </h3>
        </div>

        <form action={formAction} className="space-y-4">
          <input type="hidden" name="from" value={from} />
          <FormError message={state.error} />
          <Field
            label="Email"
            name="email"
            type="email"
            placeholder="operation.team@gdce.local"
            autoComplete="username"
            error={state.fieldErrors?.email}
            // defaultValue="lipz@gmail.com"
          />
          <Field
            label="Password"
            name="password"
            type="password"
            placeholder="••••••••"
            autoComplete="current-password"
            error={state.fieldErrors?.password}
          />
          <SubmitButton label="Sign in" />
        </form>

        <div className="my-5 flex items-center gap-2.5">
          <div className="h-px flex-1 bg-neutral-200" />
          <span className="text-micro tracking-[0.1em] text-neutral-400 uppercase">
            or
          </span>
          <div className="h-px flex-1 bg-neutral-200" />
        </div>

        <button
          type="button"
          disabled
          title="AD sign-in isn't wired up yet"
          className="w-full cursor-not-allowed rounded-lg border border-[oklch(0.52_0.135_254)]/30 px-4 py-2.5 text-sm text-[oklch(0.405_0.112_254)]/50"
        >
          Continue with GDCE-AD (DBA Team)
        </button>
      </div>
    </div>
  );
}
