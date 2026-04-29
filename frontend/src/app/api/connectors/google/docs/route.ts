import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

export async function POST(req: NextRequest) {
  const token = (await cookies()).get("rag-session")?.value;
  const body  = await req.json() as { title?: string; content: string };

  const res = await fetch(`${BACKEND}/api/v1/connectors/google/docs`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });

  const data = await res.json() as { url?: string; error?: string };
  return NextResponse.json(data, { status: res.status });
}
