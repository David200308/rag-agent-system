import { request } from "undici";
import { cookies } from "next/headers";

const SCHEDULER = process.env.SCHEDULER_URL ?? "http://localhost:8082";

async function getToken() {
  const cookieStore = await cookies();
  return cookieStore.get("rag-session")?.value;
}

/** PATCH /api/scheduler/schedules/[id] */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const token = await getToken();
  const bodyText = await req.text();

  const { statusCode, headers, body } = await request(`${SCHEDULER}/schedules/${id}`, {
    method: "PATCH",
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

/** DELETE /api/scheduler/schedules/[id] */
export async function DELETE(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;
  const token = await getToken();

  const { statusCode } = await request(`${SCHEDULER}/schedules/${id}`, {
    method: "DELETE",
    headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) },
  });
  return new Response(null, { status: statusCode });
}
