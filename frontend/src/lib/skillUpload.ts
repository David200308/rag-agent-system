import { exec } from "node:child_process";
import { promisify } from "node:util";
import { writeFile, mkdir, readFile } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { backendFetch } from "@/lib/backend-client";

const execAsync = promisify(exec);

const BINARY_EXTS = new Set([
  ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp", ".ico", ".svg",
  ".pdf", ".zip", ".tar", ".gz", ".bz2", ".7z", ".rar",
  ".jar", ".class", ".pyc", ".pyo", ".pyd",
  ".exe", ".dll", ".so", ".dylib", ".bin",
  ".woff", ".woff2", ".ttf", ".eot", ".otf",
  ".mp3", ".mp4", ".wav", ".avi", ".mov", ".mkv", ".flv",
  ".db", ".sqlite", ".pkl", ".npz", ".npy",
]);

/** Formats whose bytes are forwarded as-is — the Java backend runs Tika to extract text. */
const BINARY_CONTENT_TYPES: Record<string, string> = {
  pdf: "application/pdf",
  docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
};

async function extractZipContent(zipBuffer: ArrayBuffer): Promise<string> {
  const tmpDir  = path.join(os.tmpdir(), `skill-zip-${Date.now()}`);
  const zipPath = `${tmpDir}.zip`;
  await writeFile(zipPath, Buffer.from(zipBuffer));
  await mkdir(tmpDir, { recursive: true });
  try {
    await execAsync(`unzip -o "${zipPath}" -d "${tmpDir}"`);
    const { stdout } = await execAsync(
      `find "${tmpDir}" -type f -not -path '*/__MACOSX/*' -not -name '.DS_Store' -not -name '*.DS_Store' | sort`
    );
    const files = stdout.trim().split("\n").filter(Boolean);
    const sections = await Promise.all(
      files.map(async f => {
        const rel = path.relative(tmpDir, f);
        const ext = path.extname(f).toLowerCase();
        if (BINARY_EXTS.has(ext)) {
          return `<<< ${rel} >>>\n(binary file — preview not available)`;
        }
        const content = await readFile(f, "utf-8").catch(() => "(binary file — preview not available)");
        return `<<< ${rel} >>>\n${content}`;
      }),
    );
    return sections.join("\n\n");
  } finally {
    await execAsync(`rm -rf "${tmpDir}" "${zipPath}"`).catch(() => {});
  }
}

export type ParsedUpload =
  | { kind: "text"; content: string; ext: string }
  | { kind: "binary"; bytes: ArrayBuffer; ext: string; contentType: string };

/**
 * Validates the extension and produces what should actually be uploaded:
 *  - .zip            → flattened `<<< filename >>>`-annotated text (existing behavior)
 *  - .txt/.md/.csv    → decoded UTF-8 text
 *  - .pdf/.docx       → raw bytes, untouched — the Java backend runs Tika to extract text,
 *                       since decoding a binary format as UTF-8 here would corrupt it
 */
export async function parseUploadedFile(file: File): Promise<ParsedUpload> {
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";

  if (ext === "zip") {
    return { kind: "text", content: await extractZipContent(await file.arrayBuffer()), ext };
  }
  if (ext === "txt" || ext === "md" || ext === "csv") {
    const buffer = await file.arrayBuffer();
    return { kind: "text", content: Buffer.from(buffer).toString("utf-8"), ext };
  }
  if (ext in BINARY_CONTENT_TYPES) {
    return { kind: "binary", bytes: await file.arrayBuffer(), ext, contentType: BINARY_CONTENT_TYPES[ext]! };
  }
  throw new Error("Unsupported file type");
}

/**
 * Forwards a parsed upload to the backend as multipart/form-data — never as a JSON
 * string. This is what lets large markdown/zip uploads through without erroring on a
 * giant in-memory JSON body, and what lets PDF/DOCX bytes reach Tika intact.
 */
export async function forwardSkillMultipart(
  url: string,
  parsed: ParsedUpload,
  filename: string,
  fields: Record<string, string>,
  authHeaders: Record<string, string | undefined>,
): Promise<Response> {
  const form = new FormData();
  if (parsed.kind === "text") {
    form.append("file", new Blob([parsed.content], { type: "text/plain" }), filename);
  } else {
    form.append("file", new Blob([parsed.bytes], { type: parsed.contentType }), filename);
  }
  for (const [key, value] of Object.entries(fields)) {
    form.append(key, value);
  }
  const headers = Object.fromEntries(
    Object.entries(authHeaders).filter(([, v]) => v !== undefined),
  ) as Record<string, string>;
  return backendFetch(url, { method: "POST", headers, body: form });
}
