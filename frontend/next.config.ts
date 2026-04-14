import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",

  // No rewrites needed — Next.js API routes (using undici) proxy to Spring Boot.

  /**
   * Server-side environment variables.
   *
   * These are read at runtime by:
   *  - middleware.ts           → AUTH_ENABLED (redirect to /login or not)
   *  - /api/auth/config        → AUTH_ENABLED (tells the client whether to show login)
   *  - /api/auth/verify-otp   → AUTH_JWT_EXPIRY_HOURS (cookie maxAge)
   *  - /api/agent/* routes    → BACKEND_URL (Spring Boot proxy target)
   *
   * Copy frontend/.env.local.example → frontend/.env.local and fill in the values.
   * In production, set these as real environment variables (Vercel, Docker, etc.).
   */
  env: {
    // Toggles login on/off. Set to "false" to bypass auth entirely.
    AUTH_ENABLED: process.env.AUTH_ENABLED ?? "true",
    // Must match auth.jwt-expiry-hours in application.yml.
    AUTH_JWT_EXPIRY_HOURS: process.env.AUTH_JWT_EXPIRY_HOURS ?? "24",
  },
};

export default nextConfig;
