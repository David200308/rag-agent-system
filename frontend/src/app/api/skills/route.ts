import { readFile, writeFile, mkdir, unlink } from "node:fs/promises";
import { existsSync } from "node:fs";
import { exec } from "node:child_process";
import { promisify } from "node:util";
import { randomUUID } from "node:crypto";
import path from "node:path";
import os from "node:os";
import type { NextRequest } from "next/server";
import type { Skill } from "@/types/agent";

const execAsync = promisify(exec);

const DATA_DIR = path.join(process.cwd(), "data", "skills");
const MANIFEST = path.join(DATA_DIR, "manifest.json");

async function ensureDir() {
  if (!existsSync(DATA_DIR)) await mkdir(DATA_DIR, { recursive: true });
}

async function readManifest(): Promise<Skill[]> {
  try {
    const raw = await readFile(MANIFEST, "utf-8");
    return JSON.parse(raw) as Skill[];
  } catch {
    return [];
  }
}

async function writeManifest(skills: Skill[]) {
  await writeFile(MANIFEST, JSON.stringify(skills, null, 2), "utf-8");
}

async function extractZipContent(zipBuffer: ArrayBuffer): Promise<string> {
  const tmpDir = await import("node:fs/promises").then(() =>
    path.join(os.tmpdir(), `skill-zip-${Date.now()}`)
  );
  const zipPath = `${tmpDir}.zip`;
  await writeFile(zipPath, Buffer.from(zipBuffer));
  await mkdir(tmpDir, { recursive: true });

  try {
    await execAsync(`unzip -o "${zipPath}" -d "${tmpDir}"`);
    const { stdout } = await execAsync(
      `find "${tmpDir}" -type f \\( -name "*.txt" -o -name "*.md" \\) | sort`
    );
    const files = stdout.trim().split("\n").filter(Boolean);
    const contents = await Promise.all(
      files.map((f) => readFile(f, "utf-8").catch(() => ""))
    );
    return contents.join("\n\n");
  } finally {
    await execAsync(`rm -rf "${tmpDir}" "${zipPath}"`).catch(() => {});
  }
}

export async function GET() {
  await ensureDir();
  const skills = await readManifest();
  return Response.json(skills);
}

export async function POST(req: NextRequest) {
  await ensureDir();

  const form = await req.formData();
  const file = form.get("file") as File | null;
  const name = (form.get("name") as string | null)?.trim() || "";

  if (!file) return Response.json({ error: "No file" }, { status: 400 });

  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (!["txt", "md", "zip"].includes(ext)) {
    return Response.json({ error: "Unsupported file type" }, { status: 400 });
  }

  const id = randomUUID();
  const buffer = await file.arrayBuffer();

  let content: string;
  if (ext === "zip") {
    content = await extractZipContent(buffer);
  } else {
    content = Buffer.from(buffer).toString("utf-8");
  }

  const contentPath = path.join(DATA_DIR, `${id}.txt`);
  await writeFile(contentPath, content, "utf-8");

  const skill: Skill = {
    id,
    name: name || file.name.replace(/\.[^.]+$/, ""),
    fileName: file.name,
    fileType: ext as Skill["fileType"],
    size: file.size,
    createdAt: new Date().toISOString(),
  };

  const skills = await readManifest();
  skills.unshift(skill);
  await writeManifest(skills);

  return Response.json(skill, { status: 201 });
}
