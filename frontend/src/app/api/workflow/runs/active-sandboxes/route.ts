import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeader() {
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

export async function GET() {
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/workflow/runs/active-sandboxes`,
    { method: "GET", headers: await authHeader() },
  );
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}
