import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const store = await cookies();
  return store.get("rag-session")?.value;
}

/** GET /api/team/approvals — list pending KB + skills (owner only) */
export async function GET() {
  const token = await getToken();
  const { statusCode, body } = await request(`${BACKEND}/api/v1/team/approvals`, {
    headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) },
  });
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
