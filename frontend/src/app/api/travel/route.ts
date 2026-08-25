import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

export async function GET() {
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/travel`,
    { method: "GET", headers: await authHeaders() },
  );
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}

export async function POST(req: NextRequest) {
  const payload = await req.text();
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/travel`,
    {
      method: "POST",
      headers: { "content-type": "application/json", ...await authHeaders() },
      body: payload,
    },
  );
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}
