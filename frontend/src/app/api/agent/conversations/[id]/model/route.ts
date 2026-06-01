import { request } from "@/lib/backend-client";
import { cookies } from "next/headers";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

type Ctx = { params: Promise<{ id: string }> };

export async function PATCH(req: Request, { params }: Ctx) {
  const { id } = await params;
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  if (!token) return Response.json({ error: "Unauthenticated" }, { status: 401 });

  const bodyText = await req.text();
  const { statusCode, body } = await request(
    `${BACKEND}/api/v1/agent/conversations/${id}/model`,
    {
      method: "PATCH",
      headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
      body: bodyText,
    },
  );
  return new Response(await body.text(), {
    status: statusCode,
    headers: { "content-type": "application/json" },
  });
}
