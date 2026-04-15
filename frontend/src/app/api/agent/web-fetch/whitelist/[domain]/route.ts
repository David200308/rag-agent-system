import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/** DELETE /api/agent/web-fetch/whitelist/[domain] */
export async function DELETE(
  _req: Request,
  { params }: { params: Promise<{ domain: string }> },
) {
  const { domain } = await params;
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;

  const { statusCode } = await request(
    `${BACKEND}/api/v1/agent/web-fetch/whitelist/${encodeURIComponent(domain)}`,
    { method: "DELETE", headers: { ...(token ? { authorization: `Bearer ${token}` } : {}) } },
  );
  return new Response(null, { status: statusCode });
}
