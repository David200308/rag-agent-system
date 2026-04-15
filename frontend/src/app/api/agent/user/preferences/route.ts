import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const cookieStore = await cookies();
  return cookieStore.get("rag-session")?.value;
}

/** GET /api/agent/user/preferences */
export async function GET() {
  const token = await getToken();
  if (!token) return Response.json({ error: "Unauthenticated" }, { status: 401 });

  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/user/preferences`,
    { headers: { authorization: `Bearer ${token}` } },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}

/** PUT /api/agent/user/preferences */
export async function PUT(req: Request) {
  const token = await getToken();
  if (!token) return Response.json({ error: "Unauthenticated" }, { status: 401 });

  const bodyText = await req.text();
  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/user/preferences`,
    {
      method: "PUT",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${token}`,
      },
      body: bodyText,
    },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
