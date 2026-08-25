import type { NextRequest} from "next/server";
import { NextResponse } from "next/server";

/**
 * Middleware — runs on every matching request (Edge runtime).
 *
 * Auth is fully skippable via the AUTH_ENABLED env var (defaults to true).
 * Set AUTH_ENABLED=false in .env.local to open the app without login.
 *
 * Protected: all pages and /api/agent/* proxy routes.
 * Public:    /, /login, /register, /api/auth/*, Next.js internals (_next/*).
 *
 * "/" is special-cased: it's the public marketing landing page, but the
 * actual chat app lives at /home. Signed-in users (or when auth is disabled)
 * get "/" transparently rewritten to /home so the URL bar still reads "/".
 */

const AUTH_ENABLED = process.env.AUTH_ENABLED !== "false";

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Always allow public paths regardless of auth config
  const isPublic =
    pathname.startsWith("/login") ||
    pathname.startsWith("/register") ||
    pathname.startsWith("/api/auth/") ||
    pathname.startsWith("/_next/") ||
    pathname === "/favicon.ico" ||
    // Static files served from /public (logos, icons, etc.) — needed
    // unauthenticated on the landing/login pages, and never sensitive.
    /\.(svg|png|jpe?g|gif|webp|avif|ico|css|js|map|woff2?|ttf)$/.test(pathname);

  if (isPublic) return NextResponse.next();

  const token = request.cookies.get("rag-session")?.value;

  if (pathname === "/") {
    // No auth required to reach "/", but signed-in users (or auth-disabled
    // deployments) should land in the app rather than the landing page.
    if (!AUTH_ENABLED || token) {
      const appUrl = request.nextUrl.clone();
      appUrl.pathname = "/home";
      return NextResponse.rewrite(appUrl);
    }
    return NextResponse.next();
  }

  // Auth disabled — let everything else through
  if (!AUTH_ENABLED) return NextResponse.next();

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
