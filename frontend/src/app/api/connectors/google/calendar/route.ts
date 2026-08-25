import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const store = await cookies();
  return store.get("rag-session")?.value;
}

/** GET /api/connectors/google/calendar?maxResults=10 — list upcoming events */
export async function GET(req: NextRequest) {
  const token = await getToken();
  const maxResults = req.nextUrl.searchParams.get("maxResults") ?? "10";

  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/connectors/google/calendar/events?maxResults=${maxResults}`,
    { headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) } },
  );
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}

/** POST /api/connectors/google/calendar — create a calendar event */
export async function POST(req: NextRequest) {
  const token = await getToken();
  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/connectors/google/calendar/events`,
    {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...(token ? { authorization: `Bearer ${token}` } : {}),
      },
      body: await req.text(),
    },
  );
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
