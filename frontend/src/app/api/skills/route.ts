import { request } from "undici";
import { cookies } from "next/headers";
import { exec } from "node:child_process";
import { promisify } from "node:util";
import { writeFile, mkdir, readFile } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import type { NextRequest } from "next/server";

const execAsync = promisify(exec);
const BACKEND = process.env.BACKEND_URL ?? "http://localhost:8081";

async function authHeaders() {
  const store = await cookies();
  const token = store.get("rag-session")?.value;
  return token ? { authorization: `Bearer ${token}` } : {};
}

async function extractZipContent(zipBuffer: ArrayBuffer): Promise<string> {
  const tmpDir  = path.join(os.tmpdir(), `skill-zip-${Date.now()}`);
  const zipPath = `${tmpDir}.zip`;
  await writeFile(zipPath, Buffer.from(zipBuffer));
  await mkdir(tmpDir, { recursive: true });
  try {
    await execAsync(`unzip -o "${zipPath}" -d "${tmpDir}"`);
    const { stdout } = await execAsync(
      `find "${tmpDir}" -type f \\( -name "*.txt" -o -name "*.md" \\) | sort`
    );
    const files    = stdout.trim().split("\n").filter(Boolean);
    const contents = await Promise.all(files.map(f => readFile(f, "utf-8").catch(() => "")));
    return contents.join("\n\n");
  } finally {
    await execAsync(`rm -rf "${tmpDir}" "${zipPath}"`).catch(() => {});
  }
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

  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!["txt", "md", "zip"].includes(ext)) {
    return Response.json({ error: "Unsupported file type" }, { status: 400 });
  }

  const buffer  = await file.arrayBuffer();
  const content = ext === "zip"
    ? await extractZipContent(buffer)
    : Buffer.from(buffer).toString("utf-8");

  const payload = {
    name:     name || file.name.replace(/\.[^.]+$/, ""),
    fileName: file.name,
    fileType: ext,
    size:     file.size,
    content,
  };

  const { statusCode, headers, body } = await request(
    `${BACKEND}/api/v1/skills`,
    {
      method:  "POST",
      headers: { "content-type": "application/json", ...await authHeaders() },
      body:    JSON.stringify(payload),
    },
  );
  const ct = ([] as string[]).concat(headers["content-type"] ?? "application/json")[0] ?? "application/json";
  return new Response(await body.text(), { status: statusCode, headers: { "content-type": ct } });
}
