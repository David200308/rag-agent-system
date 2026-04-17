import { request } from "undici";
import { cookies } from "next/headers";

const SCHEDULER = process.env.SCHEDULER_URL ?? "http://localhost:8082";

async function getToken() {
  const cookieStore = await cookies();
  return cookieStore.get("rag-session")?.value;
}

/** GET /api/scheduler/schedules?conversationId={id} */
export async function GET(req: Request) {
  const { searchParams } = new URL(req.url);
  const conversationId = searchParams.get("conversationId") ?? "";
  const token = await getToken();

  const { statusCode, headers, body } = await request(
    `${SCHEDULER}/schedules?conversationId=${encodeURIComponent(conversationId)}`,
    {
      method: "GET",
      headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) },
    },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: {
      "content-type":
        ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ??
        "application/json",
    },
  });
}

/** POST /api/scheduler/schedules */
export async function POST(req: Request) {
  const token = await getToken();
  const bodyText = await req.text();

  const { statusCode, headers, body } = await request(`${SCHEDULER}/schedules`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: bodyText,
  });
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: {
      "content-type":
        ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ??
        "application/json",
    },
  });
}
