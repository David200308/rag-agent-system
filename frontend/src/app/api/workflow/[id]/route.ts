import { request } from "undici";
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

export async function GET(_req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/workflow/${id}`,
    { method: "GET", headers: await authHeader() },
  );
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct(headers) } });
}

export async function PATCH(req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/workflow/${id}`,
    { method: "PATCH", headers: { "content-type": "application/json", ...await authHeader() }, body: await req.text() },
  );
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct(headers) } });
}

export async function DELETE(_req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const { statusCode } = await request(
    `${BACKEND}/api/v1/workflow/${id}`,
    { method: "DELETE", headers: await authHeader() },
  );
  return new Response(null, { status: statusCode });
}
