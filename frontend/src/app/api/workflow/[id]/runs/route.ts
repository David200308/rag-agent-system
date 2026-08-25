import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeader() {
  const store = await cookies();
  const t = store.get("rag-session")?.value;
  return t ? { authorization: `Bearer ${t}` } : {};
}
function ct(h: Record<string, string | string[] | undefined>) {
  return ([] as string[]).concat(h["content-type"] ?? "application/json")[0] ?? "application/json";
}

type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const { searchParams } = req.nextUrl;
  const page = searchParams.get("page") ?? "0";
  const size = searchParams.get("size") ?? "10";
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/workflow/${id}/runs?page=${page}&size=${size}`,
    { method: "GET", headers: await authHeader() },
  );
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct(headers) } });
}

export async function POST(req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/workflow/${id}/runs`,
    { method: "POST", headers: { "content-type": "application/json", ...await authHeader() }, body: await req.text() },
  );
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct(headers) } });
}
