import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/** DELETE /api/team/members/[email] — remove a member (owner only) */
export async function DELETE(_req: Request, { params }: { params: Promise<{ email: string }> }) {
  const { email } = await params;
  const store = await cookies();
  const token = store.get("rag-session")?.value;

  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/team/members/${encodeURIComponent(email)}`,
    {
      method: "DELETE",
      headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) },
    },
  );
  const text = await body.text();
  return new Response(text || null, {
    status: statusCode,
    headers: text ? { "content-type": "application/json" } : {},
  });
}
