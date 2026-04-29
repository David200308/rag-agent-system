import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

export async function GET(_req: NextRequest) {
  const token = (await cookies()).get("rag-session")?.value;

  const res = await fetch(`${BACKEND}/api/v1/connectors/status`, {
    headers: token ? { authorization: `Bearer ${token}` } : {},
    cache: "no-store",
  });

  const body = await res.json() as Record<string, boolean>;
  return Response.json(body, { status: res.status });
}
