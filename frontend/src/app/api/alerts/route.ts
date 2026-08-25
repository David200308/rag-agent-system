import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

/** GET /api/alerts — { price: [...], defi: [...], predictMarket: [...] } */
export async function GET() {
  const { statusCode, headers, body } = await request(`${BACKEND}/api/v1/alerts`, {
    method: "GET",
    headers: await authHeaders(),
  });
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}

/** POST /api/alerts — create a price (crypto or stock) alert rule */
export async function POST(req: Request) {
  const payload = await req.text();
  const { statusCode, headers, body } = await request(`${BACKEND}/api/v1/alerts/price`, {
    method: "POST",
    headers: { "content-type": "application/json", ...await authHeaders() },
    body: payload,
  });
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}
