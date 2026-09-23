"use client";

import { useActionState, useEffect, useState } from "react";
import { useFormStatus } from "react-dom";
import { createServerAction } from "@/src/lib/actions";
import type { AddServerState, ServerType, SystemItem } from "@/src/lib/types";

const KINDS: { value: ServerType; label: string; hint: string }[] = [
  { value: "APP", label: "App node", hint: "Application or worker host" },
  { value: "WEB", label: "Web node", hint: "Edge, proxy or web tier" },
  { value: "DATABASE", label: "DB node", hint: "Database primary or replica" },
];

const labelCls = "mb-[5px] block text-[10px] tracking-[.14em] uppercase text-[color-mix(in_srgb,var(--color-text)_70%,transparent)]";

/** "Add server" dialog from the mockup, posting to POST /api/v1/servers. */
export function AddServerDialog({ systems, onClose }: { systems: SystemItem[] | null; onClose: () => void }) {
  const [state, action] = useActionState<AddServerState, FormData>(createServerAction, {});
  const [kind, setKind] = useState<ServerType>((state.values?.serverType as ServerType) || "APP");

  useEffect(() => {
    if (state.ok) onClose();
  }, [state.ok, onClose]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const v = state.values ?? {};
  // A server must belong to a system (the API requires systemId), so the
  // mockup's "Unassigned" option is not offered.
  const noSystems = systems != null && systems.length === 0;

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-[color-mix(in_srgb,#2b2b2d_50%,transparent)] p-[13.6px]">
      <form
        action={action}
        role="dialog"
        aria-modal="true"
        aria-labelledby="add-server-title"
        className="blueprint flex w-[600px] max-w-[calc(100vw-48px)] flex-col gap-[10.2px] bg-white shadow-[var(--shadow-lg)]"
      >
        <i className="corner tl" />
        <i className="corner tr" />
        <i className="corner bl" />
        <i className="corner br" />

        <div className="flex items-start justify-between gap-5 border-b border-divider px-[26px] pt-6 pb-[18px]">
          <div>
            <div className="text-[10px] tracking-[.16em] text-accent-700 uppercase">Fleet registry</div>
            <div id="add-server-title" className="font-heading mt-1.5 text-[26px] leading-[1.05] font-semibold tracking-[.02em] uppercase">
              Add server
            </div>
            <div className="mt-1.5 max-w-[44ch] text-[12.5px] text-[#5d5d60]">
              The node joins its system group immediately and shows as reporting once its agent publishes a scrape.
            </div>
          </div>
          <button type="button" onClick={onClose} aria-label="Close" className="btn btn-ghost btn-icon flex-none">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden>
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="grid grid-cols-1 gap-x-5 gap-y-[18px] px-[26px] pt-[22px] pb-1.5 sm:grid-cols-2">
          <div>
            <label htmlFor="add-name" className={labelCls}>
              Hostname
            </label>
            <input id="add-name" name="name" className="input mono" placeholder="app-prod-12" defaultValue={v.name} autoFocus required />
          </div>
          <div>
            <label htmlFor="add-ip" className={labelCls}>
              IP address
            </label>
            <input id="add-ip" name="ip" className="input mono" placeholder="10.42.8.46" defaultValue={v.ip} required />
          </div>

          <fieldset className="sm:col-span-2">
            <legend className={labelCls}>Node type</legend>
            <input type="hidden" name="serverType" value={kind} />
            <div className="mt-[2px] grid grid-cols-1 gap-2.5 sm:grid-cols-3">
              {KINDS.map((k) => {
                const on = kind === k.value;
                return (
                  <button
                    key={k.value}
                    type="button"
                    role="radio"
                    aria-checked={on}
                    onClick={() => setKind(k.value)}
                    className={`flex cursor-pointer flex-col gap-[5px] rounded-[5px] border px-[13px] pt-3 pb-[13px] text-left ${
                      on ? "border-accent bg-accent text-white shadow-[var(--shadow-sm)]" : "border-divider bg-white text-[#424244] hover:bg-[color-mix(in_srgb,var(--color-accent)_6%,transparent)]"
                    }`}
                  >
                    <span className="flex items-center justify-between gap-2">
                      <span className="font-heading text-[14px] font-semibold tracking-[.06em] uppercase">{k.label}</span>
                      <span
                        className={`size-[13px] flex-none rounded-full border ${on ? "border-white bg-white" : "border-[#b7b7ba]"}`}
                        style={on ? { boxShadow: "inset 0 0 0 2px var(--color-accent)" } : undefined}
                      />
                    </span>
                    <span className={`text-[11.5px] leading-[1.35] ${on ? "text-[#eef3fa]" : "text-[#5d5d60]"}`}>{k.hint}</span>
                  </button>
                );
              })}
            </div>
          </fieldset>

          <div>
            <label htmlFor="add-role" className={labelCls}>
              Role
            </label>
            <input id="add-role" name="description" className="input" placeholder="checkout api" defaultValue={v.description} maxLength={500} />
          </div>
          <div>
            <label htmlFor="add-system" className={labelCls}>
              System
            </label>
            <div className="relative flex">
              <select
                id="add-system"
                name="systemId"
                className="input appearance-none pr-[30px]"
                defaultValue={v.systemId ?? ""}
                required
                disabled={!systems || noSystems}
              >
                <option value="" disabled>
                  {systems == null ? "Could not load systems" : noSystems ? "No systems yet" : "Select a system"}
                </option>
                {systems?.map((s) => (
                  <option key={s.uuid} value={s.uuid}>
                    {s.name}
                  </option>
                ))}
              </select>
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="#5d5d60"
                strokeWidth="1.5"
                className="pointer-events-none absolute top-1/2 right-[9px] -translate-y-1/2"
                aria-hidden
              >
                <path d="m6 9 6 6 6-6" />
              </svg>
            </div>
          </div>
        </div>

        {state.error ? (
          <div role="alert" className="mx-[26px] mt-3.5 border border-[#c9857d] bg-[color-mix(in_srgb,#a34a3f_8%,transparent)] px-3 py-[9px] text-[12px] text-[#8e3c32]">
            {state.error}
          </div>
        ) : null}

        <div className="mt-[22px] flex items-center justify-between gap-4 border-t border-divider px-[26px] pt-4 pb-5">
          <div className="text-[11px] tracking-[.06em] text-[#5d5d60] uppercase">Registered via /api/v1/servers</div>
          <div className="flex flex-none gap-2">
            <button type="button" onClick={onClose} className="btn btn-secondary">
              Cancel
            </button>
            <SaveButton disabled={!systems || noSystems} />
          </div>
        </div>
      </form>
    </div>
  );
}

function SaveButton({ disabled }: { disabled: boolean }) {
  const { pending } = useFormStatus();
  return (
    <button type="submit" className="btn btn-primary" disabled={disabled || pending}>
      {pending ? "Adding…" : "Add node"}
    </button>
  );
}
