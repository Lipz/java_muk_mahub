"use client";

import { useFormStatus } from "react-dom";

export function SubmitButton({ label }: { label: string }) {
  const { pending } = useFormStatus();
  return (
    <button
      type="submit"
      disabled={pending}
      className="mt-1 w-full rounded-lg bg-[oklch(0.52_0.135_254)] px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-[oklch(0.465_0.128_254)] hover:shadow-md active:bg-[oklch(0.405_0.112_254)] disabled:cursor-not-allowed disabled:opacity-60 disabled:shadow-none cursor-pointer"
    >
      {pending ? "Please wait…" : label}
    </button>
  );
}

export function Field({
  label,
  name,
  type = "text",
  placeholder,
  autoComplete,
  error,
  defaultValue,
}: {
  label: string;
  name: string;
  type?: string;
  placeholder?: string;
  autoComplete?: string;
  error?: string;
  defaultValue?: string;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-neutral-700 ">{label}</span>
      <input
        name={name}
        type={type}
        placeholder={placeholder}
        autoComplete={autoComplete}
        defaultValue={defaultValue}
        aria-invalid={Boolean(error)}
        className="w-full rounded-lg border border-neutral-300 bg-white px-3 py-2.5 text-sm text-neutral-900 shadow-sm outline-none transition focus:border-[oklch(0.52_0.135_254)] focus:ring-2 focus:ring-[oklch(0.52_0.135_254)]/15 aria-[invalid=true]:border-red-500"
      />
      {error ? <span className="mt-1 block text-xs text-red-600">{error}</span> : null}
    </label>
  );
}

export function FormError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700">
      {message}
    </p>
  );
}
