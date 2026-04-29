import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

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

  const { authUrl } = await res.json() as { authUrl: string };
  return NextResponse.redirect(authUrl);
}
