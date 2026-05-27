import { request } from "undici";

// Proxies to open.er-api.com (free, no API key required).
// Cached for 1 hour via Next.js route cache.
export const revalidate = 3600;

export async function GET() {
  try {
    const { statusCode, body } = await request(
      "https://open.er-api.com/v6/latest/USD",
      { method: "GET" },
    );
    const text = await body.text();
    if (statusCode !== 200) {
      return new Response(text, { status: statusCode, headers: { "content-type": "application/json" } });
    }
    return new Response(text, {
      status: 200,
      headers: {
        "content-type": "application/json",
        "cache-control": "public, max-age=3600",
      },
    });
  } catch {
    return Response.json({ error: "Failed to fetch exchange rates" }, { status: 502 });
  }
}
