import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeader() {
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

type Ctx = { params: Promise<{ id: string }> };

export async function POST(_req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/sandboxes/${id}/restart`,
    { method: "POST", headers: await authHeader() },
  );
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}
