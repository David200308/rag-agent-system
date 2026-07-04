import { request } from "@/lib/backend-client";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/**
 * POST /api/auth/register/verify-otp
 * Proxies to Spring Boot: POST /api/v1/auth/register/verify-otp
 * No JWT is issued here — PRE_USER can't log in until manually approved, so unlike
 * /api/auth/verify-otp there's no session cookie to set.
 */
export async function POST(req: NextRequest) {
  const body = await req.text();

  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/auth/register/verify-otp`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body,
    },
  );

  const responseBody = await upstream.text();
  return new Response(responseBody, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
