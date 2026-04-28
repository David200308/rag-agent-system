import { request } from "undici";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/** POST /api/auth/passkey/authenticate/begin — returns WebAuthn request options JSON */
export async function POST(req: NextRequest) {
  const body = await req.text();

  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/auth/passkey/authenticate/begin`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body,
    },
  );
  const text = await upstream.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
