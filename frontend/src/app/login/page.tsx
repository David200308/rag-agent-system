"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

type Step = "email" | "code";

export default function LoginPage() {
  const router = useRouter();
  const [step, setStep]       = useState<Step>("email");
  const [email, setEmail]     = useState("");
  const [code, setCode]       = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState("");
  const [checking, setChecking] = useState(true);
  const codeRef = useRef<HTMLInputElement>(null);

  // If auth is disabled, skip straight to home
  useEffect(() => {
    fetch("/api/auth/config")
      .then((r) => r.json())
      .then((data: { enabled: boolean }) => {
        if (!data.enabled) router.replace("/");
        else setChecking(false);
      })
      .catch(() => setChecking(false));
  }, [router]);

  // Focus the code input when stepping to OTP
  useEffect(() => {
    if (step === "code") codeRef.current?.focus();
  }, [step]);

  async function handleEmailSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!email.trim()) return;
    setLoading(true);
    setError("");

    try {
      const res = await fetch("/api/auth/request-otp", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: email.trim().toLowerCase() }),
      });
      const data = (await res.json()) as { message?: string; error?: string };
      if (!res.ok) throw new Error(data.error ?? "Failed to send code");
      setStep("code");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }

  async function handleCodeSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (code.length !== 6) return;
    setLoading(true);
    setError("");

    try {
      const res = await fetch("/api/auth/verify-otp", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: email.trim().toLowerCase(), code }),
      });
      const data = (await res.json()) as { success?: boolean; error?: string };
      if (!res.ok) throw new Error(data.error ?? "Invalid code");
      router.replace("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
      setCode("");
    } finally {
      setLoading(false);
    }
  }

  if (checking) {
    return (
      <div className="flex h-screen items-center justify-center bg-[--color-surface]">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-gray-900 dark:border-gray-100 border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="flex h-screen items-center justify-center bg-[--color-surface]">
      <div className="w-full max-w-sm rounded-2xl border border-[--color-border] bg-[--color-surface-raised] p-8 shadow-sm">

        {/* Logo / title */}
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-black dark:bg-white">
            <svg className="h-6 w-6 text-white dark:text-black" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round"
                d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
          </div>
          <h1 className="text-lg font-semibold">Sign in to RAG Agent</h1>
          <p className="mt-1 text-sm text-[--color-muted]">
            {step === "email"
              ? "Enter your email to receive a login code"
              : `We sent a 6-digit code to ${email}`}
          </p>
        </div>

        {/* Email step */}
        {step === "email" && (
          <form onSubmit={handleEmailSubmit} className="space-y-4">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-[--color-muted]">
                Email address
              </label>
              <input
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@company.com"
                className="w-full rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-2 text-sm outline-none
                           placeholder:text-[--color-muted] focus:border-gray-900 focus:ring-2
                           focus:ring-gray-900/10 dark:focus:border-gray-100 dark:focus:ring-gray-100/10 transition-all"
              />
            </div>

            {error && (
              <p className="rounded-lg bg-red-500/10 px-3 py-2 text-xs text-red-400">{error}</p>
            )}

            <button
              type="submit"
              disabled={loading || !email.trim()}
              className="w-full rounded-lg bg-black dark:bg-white px-4 py-2.5 text-sm font-medium text-white dark:text-black
                         transition-opacity hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "Sending…" : "Send code"}
            </button>
          </form>
        )}

        {/* Code step */}
        {step === "code" && (
          <form onSubmit={handleCodeSubmit} className="space-y-4">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-[--color-muted]">
                6-digit code
              </label>
              <input
                ref={codeRef}
                type="text"
                inputMode="numeric"
                pattern="\d{6}"
                maxLength={6}
                autoComplete="one-time-code"
                required
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
                placeholder="000000"
                className="w-full rounded-lg border border-[--color-border] bg-[--color-surface] px-3 py-2 text-center
                           font-mono text-2xl tracking-[.5em] outline-none placeholder:text-[--color-muted]
                           focus:border-gray-900 dark:border-gray-100 focus:ring-2 focus:ring-indigo-500/20 transition-all"
              />
            </div>

            {error && (
              <p className="rounded-lg bg-red-500/10 px-3 py-2 text-xs text-red-400">{error}</p>
            )}

            <button
              type="submit"
              disabled={loading || code.length !== 6}
              className="w-full rounded-lg bg-black dark:bg-white px-4 py-2.5 text-sm font-medium text-white dark:text-black
                         transition-opacity hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "Verifying…" : "Sign in"}
            </button>

            <button
              type="button"
              onClick={() => { setStep("email"); setCode(""); setError(""); }}
              className="w-full text-center text-xs text-[--color-muted] hover:text-current transition-colors"
            >
              Back — use a different email
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
