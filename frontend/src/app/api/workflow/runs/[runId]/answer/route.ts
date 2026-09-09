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

/** Answers a run paused on ASK_USER (kind=TEXT), resuming it. */
export async function POST(req: NextRequest, { params }: Ctx) {
  const { runId } = await params;
  const { statusCode } = await request(
    `${BACKEND}/api/v1/workflow/runs/${runId}/answer`,
    { method: "POST", headers: { "content-type": "application/json", ...await authHeader() }, body: await req.text() },
  );
  return new Response(null, { status: statusCode });
}
