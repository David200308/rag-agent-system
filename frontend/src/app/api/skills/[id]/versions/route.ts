import { request } from "@/lib/backend-client";
import { parseUploadedFile, forwardSkillMultipart, type ParsedUpload } from "@/lib/skillUpload";
import { cookies } from "next/headers";
import type { NextRequest } from "next/server";

const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

type Ctx = { params: Promise<{ id: string }> };

export async function GET(_req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/skills/${id}/versions`,
    { method: "GET", headers: await authHeaders() },
  );
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}

/** Upload a new version of an existing skill — same multipart-passthrough as creating one. */
export async function POST(req: NextRequest, { params }: Ctx) {
  const { id } = await params;
  const form = await req.formData();
  const file = form.get("file") as File | null;

  if (!file) return Response.json({ error: "No file" }, { status: 400 });

  let parsed: ParsedUpload;
  try {
    parsed = await parseUploadedFile(file);
  } catch {
    return Response.json({ error: "Unsupported file type" }, { status: 400 });
  }

  const res = await forwardSkillMultipart(
    `${BACKEND}/api/v1/skills/${id}/versions`,
    parsed,
    file.name,
    { fileType: parsed.ext },
    await authHeaders(),
  );
  const ct = res.headers.get("content-type") ?? "application/json";
  return new Response(await res.text(), { status: res.status, headers: { "content-type": ct } });
}
