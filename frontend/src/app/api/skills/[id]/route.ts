import { request } from "undici";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

type Ctx = { params: Promise<{ id: string }> };

export async function GET(_req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/skills/${id}/content`,
    { method: "GET", headers: await authHeaders() },
  );
  if (statusCode === 404) return Response.json({ error: "Not found" }, { status: 404 });
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "text/plain" },
  });
}

export async function DELETE(_req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const { statusCode } = await request(
    `${BACKEND}/api/v1/skills/${id}`,
    { method: "DELETE", headers: await authHeaders() },
  );
  return new Response(null, { status: statusCode });
}
