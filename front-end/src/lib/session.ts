import "server-only";
import { cookies } from "next/headers";
import type { JwtClaims } from "./types";

export const SESSION_COOKIE = "access_token";

export function decodeJwt(token: string): JwtClaims | null {
  
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const json = Buffer.from(
      payload.replace(/-/g, "+").replace(/_/g, "/"),
      "base64",
    ).toString("utf8");
    const claims = JSON.parse(json) as JwtClaims;
    if (typeof claims.exp !== "number") return null;
    
    return claims;
  } catch {
    return null;
  }
}

export function isExpired(claims : JwtClaims) : boolean {
    return claims.exp * 1000 <= Date.now();
}



export async function createSession(token:string, expiresInMs : number) {
    const store = await cookies();
    store.set(SESSION_COOKIE, token , {
        httpOnly: true,
        secure: process.env.NODE_ENV === "production",
        sameSite: 'lax',
        path : "/",
        maxAge: Math.floor(expiresInMs/ 1000)
    });
}

export async function destroySession(){
    const store = await cookies();
    store.delete(SESSION_COOKIE);
}

export async function getToken(): Promise<string | null> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) return null;
  const claims = decodeJwt(token);
  if (!claims || isExpired(claims)) return null;
  return token;
}

export async function getSession(): Promise <JwtClaims | null> {
    const token = await getToken();
    return token ? decodeJwt(token) : null;    
}