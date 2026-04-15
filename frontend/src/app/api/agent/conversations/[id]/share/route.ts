import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const cookieStore = await cookies();
  return cookieStore.get("rag-session")?.value;
}

/** GET /api/agent/conversations/[id]/share — get current share for a conversation */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const token = await getToken();

  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/agent/conversations/${id}/share`,
    { method: "GET", headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) } },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json" },
  });
}

/** POST /api/agent/conversations/[id]/share — create / replace share link */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const token = await getToken();
  const bodyText = await req.text();

  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/agent/conversations/${id}/share`,
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

/** DELETE /api/agent/conversations/[id]/share — revoke share link */
export async function DELETE(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const token = await getToken();

  const { statusCode } = await request(
    `${BACKEND}/api/v1/agent/conversations/${id}/share`,
    { method: "DELETE", headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) } },
  );
  return new Response(null, { status: statusCode });
}
