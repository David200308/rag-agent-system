import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const SCHEDULER = process.env.SCHEDULER_URL ?? "http://localhost:8082";

async function getToken() {
  const cookieStore = await cookies();
  return cookieStore.get("rag-session")?.value;
}

/** GET /api/scheduler/schedules/[id]/runs */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const token = await getToken();

  const { statusCode, headers, body } = await request(
    `${SCHEDULER}/schedules/${id}/runs`,
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
