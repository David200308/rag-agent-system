import { backendFetch } from "@/lib/backend-client";
import { NextResponse } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

export async function GET() {
  const res = await backendFetch(`${BACKEND}/api/v1/connectors/telegram/config`, {
    cache: "no-store",
  });
  if (!res.ok) return NextResponse.json({ botUsername: "" });
  return NextResponse.json(await res.json());
}
