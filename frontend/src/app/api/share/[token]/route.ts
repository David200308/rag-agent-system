import { request } from "undici";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

/** GET /api/share/[token] — public proxy to backend share endpoint, no auth required */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ token: string }> },
) {
  const { token } = await params;

  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/share/${token}`,
    { method: "GET" },
  );
  const text = await body.text();
  return new Response(text, {
    status: statusCode,
    headers: {
      "content-type": ([] as string[]).concat(
        headers["content-type"] ?? "application/json",
      )[0] ?? "application/json",
    },
  });
}
