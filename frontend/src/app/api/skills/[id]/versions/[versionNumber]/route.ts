import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

type Ctx = { params: Promise<{ id: string; versionNumber: string }> };

export async function GET(_req: NextRequest, { params }: Ctx) {
  const { id, versionNumber } = await params;
  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/skills/${id}/versions/${versionNumber}/content`,
    { method: "GET", headers: await authHeaders() },
  );
  if (statusCode === 404) return Response.json({ error: "Not found" }, { status: 404 });
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "text/plain" },
  });
}
