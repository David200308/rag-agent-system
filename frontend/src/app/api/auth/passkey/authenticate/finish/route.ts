import { request } from "undici";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";
const JWT_EXPIRY_HOURS = Number(process.env.AUTH_JWT_EXPIRY_HOURS ?? "24");

/**
 * POST /api/auth/passkey/authenticate/finish
 * On success the backend returns a JWT; store it in the httpOnly session cookie.
 */
export async function POST(req: NextRequest) {
  const body = await req.text();

  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/auth/passkey/authenticate/finish`,
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
