import { request } from "undici";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

/** GET /api/agent/knowledge — list all ingested sources */
export async function GET() {
  const { statusCode, headers, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/knowledge`,
    { method: "GET", headers: await authHeaders() },
  );
  const responseBody = await upstream.text();
  return new Response(responseBody, {
    status: statusCode,
    headers: {
      "content-type": ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json",
    },
  });
}

/** DELETE /api/agent/knowledge?source=... — delete a source */
export async function DELETE(req: NextRequest) {
  const source = req.nextUrl.searchParams.get("source");
  if (!source) return Response.json({ error: "source param required" }, { status: 400 });

  const { statusCode, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/knowledge?source=${encodeURIComponent(source)}`,
    { method: "DELETE", headers: await authHeaders() },
  );
  const text = await upstream.text();
  return new Response(text || null, { status: statusCode });
}

/** PATCH /api/agent/knowledge — update label / category */
export async function PATCH(req: NextRequest) {
  const body = await req.text();
  const { statusCode, headers, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/knowledge`,
    {
      method: "PATCH",
      headers: { "content-type": "application/json", ...await authHeaders() },
      body,
    },
  );
  const responseBody = await upstream.text();
  return new Response(responseBody, {
    status: statusCode,
    headers: {
      "content-type": ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json",
    },
  });
}

/** PUT /api/agent/knowledge — update sharing */
export async function PUT(req: NextRequest) {
  const body = await req.text();
  const { statusCode, headers, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/knowledge/share`,
    {
      method: "PUT",
      headers: { "content-type": "application/json", ...await authHeaders() },
      body,
    },
  );
  const responseBody = await upstream.text();
  return new Response(responseBody, {
    status: statusCode,
    headers: {
      "content-type": ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json",
    },
  });
}
