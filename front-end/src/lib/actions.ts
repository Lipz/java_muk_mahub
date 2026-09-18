"use server";

import { redirect } from "next/navigation";
import { ApiError, apiRequest } from "./api";
import { createSession, destroySession } from "./session";
import type { ActionState, LoginResponse, UserResponse } from "./types";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Only allow redirecting back to a path on this site — never to an absolute
 * URL a caller slipped into `?from=`, which would be an open redirect.
 */
function safeRedirect(from: FormDataEntryValue | null): string {
  const value = typeof from === "string" ? from : "";
  return value.startsWith("/") && !value.startsWith("//") ? value : "/dashboard";
}

export async function registerAction(
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const name = String(formData.get("name") ?? "").trim();
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");

  const values = { name, email };

  const fieldErrors: Record<string, string> = {};
  if (!name) fieldErrors.name = "Name is required.";
  if (!EMAIL_RE.test(email)) fieldErrors.email = "Enter a valid email address.";
  if (password.length < 6)
    fieldErrors.password = "Password must be at least 6 characters.";
  if (Object.keys(fieldErrors).length) return { fieldErrors, values };

  try {
    // 1. Create the account.
    await apiRequest<UserResponse>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ name, email, password }),
    });

    // 2. Register returns the user, not a token — log in to get one.
    const login = await apiRequest<LoginResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    await createSession(login.accessToken, login.expiresInMs);
  } catch (err) {
    if (err instanceof ApiError) return { error: err.message, values };
    throw err;
  }

  redirect("/dashboard");
}

export async function loginAction(
  _prev: ActionState,
  formData: FormData,
): Promise<ActionState> {
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const next = safeRedirect(formData.get("from"));

  const values = { email };

  const fieldErrors: Record<string, string> = {};
  if (!EMAIL_RE.test(email)) fieldErrors.email = "Enter a valid email address.";
  if (!password) fieldErrors.password = "Password is required.";
  if (Object.keys(fieldErrors).length) return { fieldErrors, values };

  try {
    const login = await apiRequest<LoginResponse>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    await createSession(login.accessToken, login.expiresInMs);
  } catch (err) {
    if (err instanceof ApiError) return { error: err.message, values };
    throw err;
  }

  redirect(next);
}

export async function logoutAction() {
  await destroySession();
  redirect("/login");
}