import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/** GET /api/agent/conversations/[id] — fetch messages for a conversation */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;

  const { statusCode, headers, body: upstream } = await request(
    `${BACKEND}/api/v1/agent/conversations/${id}`,
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
