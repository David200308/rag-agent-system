import { request } from "undici";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/**
 * POST /api/agent/ingest-text
 * Proxies plain-text ingestion to Spring Boot using undici.
 * Forwards the rag-session cookie as a Bearer token for Spring Boot auth.
 */
export async function POST(req: NextRequest) {
  const body = await req.text();
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;

  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/ingest/text`,
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...(token ? { authorization: `Bearer ${token}` } : {}),
      },
      body,
    },
  );

  const responseBody = await upstream.text();
  return new Response(responseBody, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
