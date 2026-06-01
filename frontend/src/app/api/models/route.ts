import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

export async function GET() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;

  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/models`,
    { headers: token ? { authorization: `Bearer ${token}` } : {} },
  );
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
