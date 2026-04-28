import { request } from "undici";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/** GET /api/auth/passkey/status?email=... */
export async function GET(req: NextRequest) {
  const email = req.nextUrl.searchParams.get("email");
  if (!email) return Response.json({ error: "email is required" }, { status: 400 });

  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/auth/passkey/status?email=${encodeURIComponent(email)}`,
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
