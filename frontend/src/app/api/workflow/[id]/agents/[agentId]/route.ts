import { request } from "undici";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeader() {
  const store = await cookies();
  const t = store.get("rag-session")?.value;
  return t ? { authorization: `Bearer ${t}` } : {};
}

type Ctx = { params: Promise<{ id: string; agentId: string }> };

export async function DELETE(_req: NextRequest, { params }: Ctx) {
  const { id, agentId } = await params;
  const { statusCode } = await request(
    `${BACKEND}/api/v1/workflow/${id}/agents/${agentId}`,
    { method: "DELETE", headers: await authHeader() },
  );
  return new Response(null, { status: statusCode });
}
