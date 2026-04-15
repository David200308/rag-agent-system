import { request } from "undici";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/**
 * GET /api/auth/config
 * Returns runtime auth configuration. When auth is enabled, also resolves
 * the signed-in email by validating the session cookie against the backend.
 */
export async function GET() {
  const enabled = process.env.AUTH_ENABLED !== "false";

  if (!enabled) {
    return Response.json({ enabled: false });
  }

  const cookieStore = await cookies();
  const token = cookieStore.get("rag-session")?.value;

  if (!token) {
    return Response.json({ enabled: true });
  }

  try {
    const { statusCode, body } = await request(
      `${BACKEND}/api/v1/auth/validate`,
      { headers: { authorization: `Bearer ${token}` } },
    );
    if (statusCode === 200) {
      const data = (await body.json()) as { valid: boolean; email?: string };
      return Response.json({ enabled: true, email: data.email ?? null });
    }
  } catch {
    // backend unreachable — return enabled without email
  }

  return Response.json({ enabled: true });
}
