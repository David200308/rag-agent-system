import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

// /api/financial/stocks/lookup?symbol=AAPL — used by the Add Stock form to auto-fill Name.
export async function GET(req: NextRequest) {
  const symbol = req.nextUrl.searchParams.get("symbol") ?? "";
  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/financial/stocks/lookup?symbol=${encodeURIComponent(symbol)}`,
    { method: "GET", headers: await authHeaders() },
  );
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
