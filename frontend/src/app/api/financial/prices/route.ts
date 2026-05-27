import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

export async function POST() {
  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/financial/prices/refresh`,
    { method: "POST", headers: await authHeaders() },
  );
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
