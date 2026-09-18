import { NextResponse, type NextRequest } from "next/server";

const SESSION_COOKIE = "access_token";
const PROTECTED = ["/dashboard"];
const AUTH_PAGES = ["/login"];

/**
 * Coarse gate only (Next 16 renamed `middleware.ts` to `proxy.ts`): it checks
 * that a token cookie exists and has not expired. Real authorization happens
 * in the Spring API on every call; server pages re-check the session too.
 */
function tokenIsLive(token: string | undefined): boolean {
  if (!token) return false;
  try {
    const payload = token.split(".")[1];
    if (!payload) return false;
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const { exp } = JSON.parse(json) as { exp?: number };
    return typeof exp === "number" && exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const signedIn = tokenIsLive(request.cookies.get(SESSION_COOKIE)?.value);

  if (!signedIn && PROTECTED.some((p) => pathname.startsWith(p))) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    // Drop the original page's query string; keep only where to return to.
    url.search = "";
    url.searchParams.set("from", pathname + request.nextUrl.search);
    return NextResponse.redirect(url);
  }

  if (signedIn && AUTH_PAGES.includes(pathname)) {
    const url = request.nextUrl.clone();
    url.pathname = "/dashboard";
    url.search = "";
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico).*)"],
};