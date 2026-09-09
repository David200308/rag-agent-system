import { request, backendFetch } from "@/lib/backend-client";
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

  // File attachments arrive as multipart/form-data — forward as-is (rebuilt FormData
  // so the runtime picks a fresh boundary) rather than through undici's JSON path.
  if ((req.headers.get("content-type") ?? "").startsWith("multipart/form-data")) {
    const incoming = await req.formData();
    const out = new FormData();
    for (const [key, value] of incoming.entries()) out.append(key, value);
    const res = await backendFetch(`${BACKEND}/api/v1/workflow/${id}/runs`, {
      method: "POST",
      headers: await authHeader() as Record<string, string>,
      body: out,
    });
    const resCt = res.headers.get("content-type") ?? "application/json";
    return new Response(await res.text(), { status: res.status, headers: { "content-type": resCt } });
  }

  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/workflow/${id}/runs`,
    { method: "POST", headers: { "content-type": "application/json", ...await authHeader() }, body: await req.text() },
  );
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct(headers) } });
}
