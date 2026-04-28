import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const cookieStore = await cookies();
  return cookieStore.get("rag-session")?.value;
}

/** POST /api/user/passkey/register/begin — returns WebAuthn creation options JSON */
export async function POST() {
  const token = await getToken();
  if (!token) return Response.json({ error: "Unauthenticated" }, { status: 401 });

  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/user/passkey/register/begin`,
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        authorization: `Bearer ${token}`,
      },
    },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
