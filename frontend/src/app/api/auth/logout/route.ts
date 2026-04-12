import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/**
 * POST /api/auth/logout
 * Revokes the session in Spring Boot and clears the `rag-session` cookie.
 */
export async function POST() {
  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;

  if (token) {
    // Best-effort revocation — ignore errors
    try {
      await request(`${BACKEND}/api/v1/auth/logout`, {
        method: "POST",
        headers: { authorization: `Bearer ${token}` },
      });
    } catch {
      // ignore
    }
  }

  cookieStore.delete("rag-session");

  return new Response(JSON.stringify({ success: true }), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
}
