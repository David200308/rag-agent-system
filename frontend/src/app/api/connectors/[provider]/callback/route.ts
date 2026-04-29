import { NextRequest, NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ provider: string }> },
) {
  const { provider } = await params;
  const { searchParams } = new URL(req.url);

  // Use the Host header so the redirect goes back to whatever hostname the
  // browser actually used (avoids 0.0.0.0 when Next.js runs in Docker).
  const protocol = req.headers.get("x-forwarded-proto") ?? "http";
  const host     = req.headers.get("host") ?? "localhost:3000";
  const baseUrl  = `${protocol}://${host}`;

  const error = searchParams.get("error");
  const code  = searchParams.get("code");
  const state = searchParams.get("state");

  if (error || !code || !state) {
    return NextResponse.redirect(
      `${baseUrl}/mcp?error=${encodeURIComponent(error ?? "oauth_cancelled")}`,
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
      return NextResponse.redirect(`${baseUrl}/mcp?error=exchange_failed`);
    }
  } catch (err) {
    console.error("[connector/callback] exchange error:", err);
    return NextResponse.redirect(`${baseUrl}/mcp?error=exchange_failed`);
  }

  return NextResponse.redirect(`${baseUrl}/mcp?connected=${provider}`);
}
