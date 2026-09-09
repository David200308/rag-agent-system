import { request, backendFetch } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeader() {
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

/**
 * POST /api/agent/query
 * Forwards the rag-session cookie as a Bearer token for Spring Boot auth.
 * File attachments arrive as multipart/form-data — forwarded as-is (rebuilt
 * FormData so the runtime picks a fresh boundary) rather than as JSON.
 */
export async function POST(req: NextRequest) {
  if ((req.headers.get("content-type") ?? "").startsWith("multipart/form-data")) {
    const incoming = await req.formData();
    const out = new FormData();
    for (const [key, value] of incoming.entries()) out.append(key, value);

    const res = await backendFetch(`${BACKEND}/api/v1/agent/query`, {
      method: "POST",
      headers: await authHeader() as Record<string, string>,
      body: out,
    });
    const resCt = res.headers.get("content-type") ?? "application/json";
    return new Response(await res.text(), { status: res.status, headers: { "content-type": resCt } });
  }

  const body = await req.text();
  const { statusCode, headers, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/query`,
    {
      method: "POST",
      headers: { "content-type": "application/json", ...await authHeader() },
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
