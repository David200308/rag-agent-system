import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function getToken() {
  const store = await cookies();
  return store.get("rag-session")?.value;
}

/** POST /api/team/approvals/knowledge/[id]/approve|reject */
export async function POST(
  _req: Request,
  { params }: { params: Promise<{ id: string; action: string }> },
) {
  const { id, action } = await params;
  if (action !== "approve" && action !== "reject") {
    return Response.json({ error: "Invalid action" }, { status: 400 });
  }
  const token = await getToken();
  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/team/approvals/knowledge/${id}/${action}`,
    {
      method: "POST",
      headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) },
    },
  );
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
