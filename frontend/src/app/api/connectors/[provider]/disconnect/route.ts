import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

export async function DELETE(
  _req: NextRequest,
  { params }: { params: Promise<{ provider: string }> },
) {
  const { provider } = await params;
  const token = (await cookies()).get("rag-session")?.value;

  const res = await fetch(`${BACKEND}/api/v1/connectors/${provider}`, {
    method: "DELETE",
    headers: token ? { authorization: `Bearer ${token}` } : {},
  });

  return NextResponse.json({ ok: res.ok }, { status: res.ok ? 200 : 502 });
}
