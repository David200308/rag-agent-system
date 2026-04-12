import { request } from "undici";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/**
 * POST /api/agent/ingest-url
 * Proxies URL ingestion requests to Spring Boot.
 */
export async function POST(req: NextRequest) {
  const body = await req.json() as Record<string, string>;

  if (!body.url?.trim()) {
    return Response.json({ error: "url field is required" }, { status: 400 });
  }

  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/ingest/url`,
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    },
  );

  const responseBody = await upstream.text();
  return new Response(responseBody, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
