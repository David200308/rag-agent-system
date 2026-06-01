import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const cookieStore = await cookies();
  return cookieStore.get("rag-session")?.value;
}

/** GET /api/share/[token] — public proxy, no auth required */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ token: string }> },
) {
  const { token } = await params;

  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/share/${token}`,
    { method: "GET" },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: {
      "content-type": ([] as string[]).concat(
        headers["content-type"] ?? "application/json",
      )[0] ?? "application/json",
    },
  });
}

/** POST /api/share/[token]/query — interactive shared conversation query */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ token: string }> },
) {
  const { token } = await params;
  const authToken = await getToken();
  const bodyText  = await req.text();

  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/share/${token}/query`,
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...(authToken ? { authorization: `Bearer ${authToken}` } : {}),
      },
      body: bodyText,
    },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: {
      "content-type": ([] as string[]).concat(
        headers["content-type"] ?? "application/json",
      )[0] ?? "application/json",
    },
  });
}
