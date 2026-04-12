import { NextRequest, NextResponse } from "next/server";

/**
 * Middleware — runs on every matching request (Edge runtime).
 *
 * Auth is fully skippable via the AUTH_ENABLED env var (defaults to true).
 * Set AUTH_ENABLED=false in .env.local to open the app without login.
 *
 * Protected: all pages and /api/agent/* proxy routes.
 * Public:    /login, /api/auth/*, Next.js internals (_next/*).
 */

const AUTH_ENABLED = process.env.AUTH_ENABLED !== "false";

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Always allow public paths regardless of auth config
  const isPublic =
    pathname.startsWith("/login") ||
    pathname.startsWith("/api/auth/") ||
    pathname.startsWith("/_next/") ||
    pathname === "/favicon.ico";

  if (isPublic) return NextResponse.next();

  // Auth disabled — let everything through
  if (!AUTH_ENABLED) return NextResponse.next();

  const token = request.cookies.get("rag-session")?.value;

  if (!token) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = "/login";
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico).*)"],
};
