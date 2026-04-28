import { request } from "undici";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const cookieStore = await cookies();
  return cookieStore.get("rag-session")?.value;
}

/** POST /api/user/passkey/register/finish */
export async function POST(req: NextRequest) {
  const token = await getToken();
  if (!token) return Response.json({ error: "Unauthenticated" }, { status: 401 });

  const body = await req.text();
  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/user/passkey/register/finish`,
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${token}`,
      },
      body,
    },
  );
  const text = await upstream.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
