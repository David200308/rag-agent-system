import { request } from "undici";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/**
 * POST /api/agent/ingest
 * Forwards the raw multipart body directly to Spring Boot.
 * Avoids reconstructing FormData — the boundary is already set by the browser.
 */
export async function POST(req: NextRequest) {
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;

  const contentType = req.headers.get("content-type") ?? "";
  if (!contentType.startsWith("multipart/form-data")) {
    return Response.json({ error: "multipart/form-data required" }, { status: 400 });
  }

  const body = Buffer.from(await req.arrayBuffer());

  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/ingest`,
    {
      method: "POST",
      body,
      headers: {
        "content-type": contentType,
        ...(token ? { authorization: `Bearer ${token}` } : {}),
      },
    },
  );

  const responseBody = await upstream.text();
  return new Response(responseBody, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
