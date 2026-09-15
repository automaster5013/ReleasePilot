import { randomBytes } from "node:crypto";
import { NextRequest, NextResponse } from "next/server";
import { contentSecurityPolicy } from "./security-policy.mts";

export function proxy(request: NextRequest) {
  const nonce = randomBytes(32).toString("base64");
  const policy = contentSecurityPolicy(nonce, process.env.NODE_ENV === "development");
  const headers = new Headers(request.headers);
  // Overwrite untrusted request values; Next.js reads this policy when rendering scripts.
  headers.set("x-nonce", nonce);
  headers.set("Content-Security-Policy", policy);
  const response = NextResponse.next({ request: { headers } });
  response.headers.set("Content-Security-Policy", policy);
  response.headers.set("Cache-Control", "private, no-store");
  return response;
}

export const config = {
  matcher: ["/((?!control-api|api|health|_next/static|_next/image|favicon.ico).*)"],
};
