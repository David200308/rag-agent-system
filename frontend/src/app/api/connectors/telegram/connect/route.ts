import { cookies } from "next/headers";
import type { NextRequest} from "next/server";
import { NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/**
 * POST /api/connectors/telegram/connect
 * Proxies the Telegram Login Widget auth payload to the backend for hash validation.
 */
export async function POST(req: NextRequest) {
  const token = (await cookies()).get("rag-session")?.value;
  const body  = await req.json();

  const res = await fetch(`${BACKEND}/api/v1/connectors/telegram/connect`, {
    method:  "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });

  return NextResponse.json({ ok: res.ok }, { status: res.ok ? 200 : 400 });
}
