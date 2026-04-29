import { NextRequest, NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ provider: string }> },
) {
  const { provider } = await params;
  const { searchParams, origin } = new URL(req.url);

  const error = searchParams.get("error");
  const code  = searchParams.get("code");
  const state = searchParams.get("state");

  if (error || !code || !state) {
    return NextResponse.redirect(
      new URL(`/mcp?error=${encodeURIComponent(error ?? "oauth_cancelled")}`, origin),
    );
  }

  try {
    const res = await fetch(`${BACKEND}/api/v1/connectors/${provider}/exchange`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code, state }),
    });

    if (!res.ok) {
      const text = await res.text();
      console.error(`[connector/callback] exchange failed: ${res.status} ${text}`);
      return NextResponse.redirect(new URL("/mcp?error=exchange_failed", origin));
    }
  } catch (err) {
    console.error("[connector/callback] exchange error:", err);
    return NextResponse.redirect(new URL("/mcp?error=exchange_failed", origin));
  }

  return NextResponse.redirect(new URL(`/mcp?connected=${provider}`, origin));
}
