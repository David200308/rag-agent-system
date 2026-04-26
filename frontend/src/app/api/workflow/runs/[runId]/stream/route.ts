import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

type Ctx = { params: Promise<{ runId: string }> };

/**
 * Proxies the SSE stream from Spring Boot to the browser.
 * Uses native fetch with ReadableStream so headers are preserved correctly.
 */
export async function GET(_req: NextRequest, { params }: Ctx) {
  const { runId } = await params;
  const store = await cookies();
  const token = store.get("rag-session")?.value;

  const upstream = await fetch(`${BACKEND}/api/v1/workflow/runs/${runId}/stream`, {
    headers: {
      accept: "text/event-stream",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
  });

  return new Response(upstream.body, {
    status: upstream.status,
    headers: {
      "content-type": "text/event-stream",
      "cache-control": "no-cache",
      "x-accel-buffering": "no",
    },
  });
}
