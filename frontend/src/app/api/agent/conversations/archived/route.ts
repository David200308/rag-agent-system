import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/** GET /api/agent/conversations/archived — list archived conversations */
export async function GET() {
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;

  const { statusCode, headers, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/conversations/archived`,
    {
      method: "GET",
      headers: {
        ...(token ? { authorization: `Bearer ${token}` } : {}),
      },
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
