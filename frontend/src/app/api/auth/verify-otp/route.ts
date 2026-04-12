import { request } from "undici";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";
// Match the JWT expiry configured in Spring Boot (auth.jwt-expiry-hours)
const JWT_EXPIRY_HOURS = Number(process.env.AUTH_JWT_EXPIRY_HOURS ?? "24");

/**
 * POST /api/auth/verify-otp
 * Proxies to Spring Boot: POST /api/v1/auth/verify-otp
 * On success, stores the JWT in an httpOnly cookie (`rag-session`).
 */
export async function POST(req: NextRequest) {
  const body = await req.text();

  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/auth/verify-otp`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body,
    },
  );

  const responseText = await upstream.text();

  if (statusCode === 200) {
    const data = JSON.parse(responseText) as { token: string };
    const cookieStore = await cookies();
    cookieStore.set("rag-session", data.token, {
      httpOnly: true,
      sameSite: "lax",
      path: "/",
      maxAge: JWT_EXPIRY_HOURS * 3600,
      // secure: true — uncomment when deployed behind HTTPS
    });
    return new Response(JSON.stringify({ success: true }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  }

  return new Response(responseText, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
