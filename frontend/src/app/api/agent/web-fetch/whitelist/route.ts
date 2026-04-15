import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const cookieStore = await cookies();
  return cookieStore.get("rag-session")?.value;
}

/** GET /api/agent/web-fetch/whitelist */
export async function GET() {
  const token = await getToken();

  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/agent/web-fetch/whitelist`,
    { method: "GET", headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) } },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json" },
  });
}

/** POST /api/agent/web-fetch/whitelist — add a domain */
export async function POST(req: Request) {
  const token = await getToken();
  const bodyText = await req.text();

  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/agent/web-fetch/whitelist`,
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...(token ? { authorization: `Bearer ${token}` } : {}),
      },
      body: bodyText,
    },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json" },
  });
}
