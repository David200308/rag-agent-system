import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const store = await cookies();
  return store.get("rag-session")?.value;
}

/** GET /api/team/members — list members of the caller's org */
export async function GET() {
  const token = await getToken();
  const { statusCode, body } = await request(`${BACKEND}/api/v1/team/members`, {
    headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) },
  });
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}

/** POST /api/team/members — add a member (owner only) */
export async function POST(req: Request) {
  const token = await getToken();
  const { statusCode, body } = await request(`${BACKEND}/api/v1/team/members`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: await req.text(),
  });
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
