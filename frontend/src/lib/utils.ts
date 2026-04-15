import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Merge Tailwind classes safely — drops conflicting class names. */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

/**
 * Format a timestamp to a short human-readable string in the given IANA timezone.
 * Shows "HH:MM" when the date is today; "MMM D, HH:MM" on any other day.
 */
export function formatTime(iso: string | Date, timeZone?: string): string {
  const d = typeof iso === "string" ? new Date(iso) : iso;
  const opts: Intl.DateTimeFormatOptions = { hour: "2-digit", minute: "2-digit", timeZone };

  // Check if d falls on today in the given timezone
  const todayStr = new Intl.DateTimeFormat("en-CA", { timeZone }).format(new Date());
  const dStr     = new Intl.DateTimeFormat("en-CA", { timeZone }).format(d);
  if (dStr !== todayStr) {
    opts.month = "short";
    opts.day   = "numeric";
  }

  return d.toLocaleString([], opts);
}

/** Truncate text to maxLength, appending ellipsis. */
export function truncate(text: string, maxLength: number): string {
  if (text.length <= maxLength) return text;
  return text.slice(0, maxLength).trimEnd() + "…";
}

/** Generate a simple random ID. */
export function randomId(): string {
  return crypto.randomUUID();
}

/** Format milliseconds as "1.2s" or "230ms". */
export function formatDuration(ms: number): string {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;
}

/** Derive a conversation title from the first user message. */
export function deriveTitle(query: string): string {
  return query.length > 50 ? query.slice(0, 47) + "…" : query;
}
