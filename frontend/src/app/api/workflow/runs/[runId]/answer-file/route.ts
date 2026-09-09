import { backendFetch } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeader() {
  const store = await cookies();
  const t = store.get("rag-session")?.value;
  return t ? { authorization: `Bearer ${t}` } : {};
}

type Ctx = { params: Promise<{ runId: string }> };

/** Answers a run paused on ASK_USER (kind=FILE) with an uploaded file, resuming it. */
export async function POST(req: NextRequest, { params }: Ctx) {
  const { runId } = await params;
  const incoming = await req.formData();
  const out = new FormData();
  for (const [key, value] of incoming.entries()) out.append(key, value);

  const res = await backendFetch(`${BACKEND}/api/v1/workflow/runs/${runId}/answer-file`, {
    method: "POST",
    headers: await authHeader() as Record<string, string>,
    body: out,
  });
  return new Response(null, { status: res.status });
}
