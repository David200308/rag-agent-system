import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/**
 * GET /api/connectors/[provider]/connect-url
 *
 * Returns { authUrl } without redirecting — used by providers like Telegram
 * where the frontend opens the URL in a new tab instead of doing a full-page redirect.
 */
export async function GET(
  _req: NextRequest,
  { params }: { params: Promise<{ provider: string }> },
) {
  const { provider } = await params;
  const token = (await cookies()).get("rag-session")?.value;

  const res = await fetch(`${BACKEND}/api/v1/connectors/${provider}/auth-url`, {
    headers: token ? { authorization: `Bearer ${token}` } : {},
    cache: "no-store",
  });

  if (!res.ok) {
    return NextResponse.json({ error: "Failed to get auth URL" }, { status: 502 });
  }

  const data = await res.json() as { authUrl: string };
  return NextResponse.json(data);
}
