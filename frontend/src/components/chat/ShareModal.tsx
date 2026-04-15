"use client";

import { useEffect, useState } from "react";
import { X, Copy, Check, Link2, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Spinner } from "@/components/ui/Spinner";
import { createShare, getShare, revokeShare } from "@/lib/api";
import type { ConversationShare } from "@/types/agent";

interface ShareModalProps {
  conversationId: string;
  onClose: () => void;
}

const EXPIRY_OPTIONS = [
  { label: "1 day",   value: 1 },
  { label: "7 days",  value: 7 },
  { label: "30 days", value: 30 },
  { label: "Never",   value: null },
] as const;

function formatExpiry(expiresAt: string | null): string {
  if (!expiresAt) return "Never expires";
  const date = new Date(expiresAt);
  const diff = date.getTime() - Date.now();
  if (diff <= 0) return "Expired";
  const days = Math.ceil(diff / 86_400_000);
  return `Expires in ${days} day${days !== 1 ? "s" : ""}`;
}

export function ShareModal({ conversationId, onClose }: ShareModalProps) {
  const [share, setShare]           = useState<ConversationShare | null | undefined>(undefined);
  const [expireDays, setExpireDays] = useState<number | null>(7);
  const [loading, setLoading]       = useState(false);
  const [copied, setCopied]         = useState(false);
  const [error, setError]           = useState<string | null>(null);

  // Load existing share on open
  useEffect(() => {
    getShare(conversationId)
      .then(setShare)
      .catch(() => setShare(null));
  }, [conversationId]);

  const shareUrl = share
    ? `${window.location.origin}/share/${share.token}`
    : null;

  const handleCreate = async () => {
    setLoading(true);
    setError(null);
    try {
      const created = await createShare(conversationId, expireDays);
      setShare(created);
    } catch {
      setError("Failed to create share link.");
    } finally {
      setLoading(false);
    }
  };

  const handleRevoke = async () => {
    setLoading(true);
    setError(null);
    try {
      await revokeShare(conversationId);
      setShare(null);
    } catch {
      setError("Failed to revoke share link.");
    } finally {
      setLoading(false);
    }
  };

  const handleCopy = async () => {
    if (!shareUrl) return;
    await navigator.clipboard.writeText(shareUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="w-full max-w-md rounded-xl border border-[--color-border] bg-white dark:bg-neutral-900 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[--color-border] px-5 py-4">
          <div className="flex items-center gap-2">
            <Link2 className="h-4 w-4" />
            <h2 className="text-sm font-semibold">Share conversation</h2>
          </div>
          <Button variant="ghost" size="icon" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        <div className="px-5 py-4 space-y-4">
          {/* Loading state */}
          {share === undefined && (
            <div className="flex justify-center py-4">
              <Spinner className="h-5 w-5" />
            </div>
          )}

          {/* No share yet — create form */}
          {share === null && (
            <>
              <p className="text-sm text-[--color-muted]">
                Anyone with the link can read this conversation (read-only).
              </p>

              <div>
                <p className="mb-2 text-xs font-medium text-[--color-muted] uppercase tracking-wide">
                  Link expires
                </p>
                <div className="flex flex-wrap gap-2">
                  {EXPIRY_OPTIONS.map((opt) => (
                    <button
                      key={String(opt.value)}
                      type="button"
                      onClick={() => setExpireDays(opt.value)}
                      className={[
                        "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                        expireDays === opt.value
                          ? "border-gray-900 bg-gray-900 text-white dark:border-gray-100 dark:bg-gray-100 dark:text-black"
                          : "border-[--color-border] text-[--color-muted] hover:border-gray-400",
                      ].join(" ")}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>

              {error && <p className="text-xs text-red-500">{error}</p>}

              <Button onClick={handleCreate} loading={loading} className="w-full">
                Create share link
              </Button>
            </>
          )}

          {/* Share exists — show URL */}
          {share && (
            <>
              <p className="text-xs text-[--color-muted]">{formatExpiry(share.expiresAt)}</p>

              <div className="flex items-center gap-2 rounded-lg border border-[--color-border] bg-[--color-surface-raised] px-3 py-2">
                <p className="flex-1 truncate text-xs font-mono text-[--color-muted]">
                  {shareUrl}
                </p>
                <button
                  type="button"
                  onClick={handleCopy}
                  className="shrink-0 rounded p-1 hover:bg-[--color-border]/50 transition-colors"
                  title="Copy link"
                >
                  {copied
                    ? <Check className="h-3.5 w-3.5 text-green-500" />
                    : <Copy className="h-3.5 w-3.5 text-[--color-muted]" />}
                </button>
              </div>

              {error && <p className="text-xs text-red-500">{error}</p>}

              <div className="flex gap-2">
                <Button onClick={handleCreate} loading={loading} className="flex-1" size="sm">
                  Regenerate link
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  loading={loading}
                  onClick={handleRevoke}
                  className="flex items-center gap-1.5"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                  Revoke
                </Button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
