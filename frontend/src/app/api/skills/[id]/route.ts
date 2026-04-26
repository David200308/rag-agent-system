import { readFile, writeFile, unlink } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import type { NextRequest } from "next/server";
import type { Skill } from "@/types/agent";

const DATA_DIR = path.join(process.cwd(), "data", "skills");
const MANIFEST = path.join(DATA_DIR, "manifest.json");

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

export async function GET(
  _req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const contentPath = path.join(DATA_DIR, `${id}.txt`);
  if (!existsSync(contentPath)) {
    return Response.json({ error: "Not found" }, { status: 404 });
  }
  const content = await readFile(contentPath, "utf-8");
  return new Response(content, { headers: { "content-type": "text/plain" } });
}

export async function DELETE(
  _req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const skills = await readManifest();
  const filtered = skills.filter((s) => s.id !== id);
  await writeManifest(filtered);

  const contentPath = path.join(DATA_DIR, `${id}.txt`);
  if (existsSync(contentPath)) await unlink(contentPath).catch(() => {});

  return new Response(null, { status: 204 });
}
