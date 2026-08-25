import { request } from "@/lib/backend-client";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

export async function GET(
  _req: NextRequest,
  { params }: { params: Promise<{ orgId: string }> },
) {
  const { orgId } = await params;
  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/auth/org/${encodeURIComponent(orgId)}`,
    { method: "GET" },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
