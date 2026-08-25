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

type Ctx = { params: Promise<{ id: string; versionNumber: string }> };

export async function POST(_req: NextRequest, { params }: Ctx) {
  const { id, versionNumber } = await params;
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/workflow/${id}/versions/${versionNumber}/restore`,
    { method: "POST", headers: { "content-type": "application/json", ...await authHeader() }, body: "{}" },
  );
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct(headers) } });
}
