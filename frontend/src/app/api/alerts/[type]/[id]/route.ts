import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

type Ctx = { params: Promise<{ type: string; id: string }> };

/** PATCH /api/alerts/[type]/[id] — update threshold/direction/enabled/frequency */
export async function PATCH(req: NextRequest, { params }: Ctx) {
  const { type, id } = await params;
  const payload = await req.text();
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/alerts/${type}/${id}`,
    {
      method: "PATCH",
      headers: { "content-type": "application/json", ...await authHeaders() },
      body: payload,
    },
  );
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}

/** DELETE /api/alerts/[type]/[id] */
export async function DELETE(req: NextRequest, { params }: Ctx) {
  const { type, id } = await params;
  const { statusCode } = await request(
    `${BACKEND}/api/v1/alerts/${type}/${id}`,
    { method: "DELETE", headers: await authHeaders() },
  );
  return new Response(null, { status: statusCode });
}
