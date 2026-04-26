import { request } from "undici";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeader() {
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

export async function GET() {
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/workflow`,
    { method: "GET", headers: await authHeader() },
  );
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": ct(headers) },
  });
}

export async function POST(req: NextRequest) {
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/workflow`,
    {
      method: "POST",
      headers: { "content-type": "application/json", ...await authHeader() },
      body: await req.text(),
    },
  );
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": ct(headers) },
  });
}

function ct(h: Record<string, string | string[] | undefined>) {
  return ([] as string[]).concat(h["content-type"] ?? "application/json")[0] ?? "application/json";
}
