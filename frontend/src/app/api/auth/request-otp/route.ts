import { request } from "undici";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/**
 * POST /api/auth/request-otp
 * Proxies to Spring Boot: POST /api/v1/auth/request-otp
 */
export async function POST(req: NextRequest) {
  const body = await req.text();

  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/auth/request-otp`,
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
