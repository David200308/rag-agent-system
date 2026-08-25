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

export async function GET() {
  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/skills`,
    { method: "GET", headers: await authHeaders() },
  );
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}

export async function POST(req: NextRequest) {
  const form = await req.formData();
  const file = form.get("file") as File | null;
  const name = (form.get("name") as string | null)?.trim() || "";

  if (!file) return Response.json({ error: "No file" }, { status: 400 });

  let parsed: ParsedUpload;
  try {
    parsed = await parseUploadedFile(file);
  } catch {
    return Response.json({ error: "Unsupported file type" }, { status: 400 });
  }

  const res = await forwardSkillMultipart(
    `${BACKEND}/api/v1/skills`,
    parsed,
    file.name,
    { name: name || file.name.replace(/\.[^.]+$/, ""), fileType: parsed.ext },
    await authHeaders(),
  );
  const ct = res.headers.get("content-type") ?? "application/json";
  return new Response(await res.text(), { status: res.status, headers: { "content-type": ct } });
}
