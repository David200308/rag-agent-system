import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeader() {
  const store = await cookies();
  const t = store.get("rag-session")?.value;
  return t ? { authorization: `Bearer ${t}` } : {};
}

type Ctx = { params: Promise<{ runId: string }> };

export async function POST(_req: NextRequest, { params }: Ctx) {
  const { runId } = await params;
  const { statusCode } = await request(
    `${BACKEND}/api/v1/workflow/runs/${runId}/stop`,
    { method: "POST", headers: await authHeader() },
  );
  return new Response(null, { status: statusCode });
}
