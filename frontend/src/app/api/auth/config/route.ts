/**
 * GET /api/auth/config
 * Returns runtime auth configuration so the client can adapt without a rebuild.
 * AUTH_ENABLED defaults to true (login required).
 */
export async function GET() {
  const enabled = process.env.AUTH_ENABLED !== "false";
  return Response.json({ enabled });
}
