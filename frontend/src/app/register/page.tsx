"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

type Step = "email" | "code" | "done";

export default function RegisterPage() {
  const router = useRouter();
  const [step, setStep]         = useState<Step>("email");
  const [email, setEmail]       = useState("");
  const [code, setCode]         = useState("");
  const [loading, setLoading]   = useState(false);
  const [error, setError]       = useState("");
  const [checking, setChecking] = useState(true);
  const [doneMessage, setDoneMessage] = useState("");
  const codeRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fetch("/api/auth/config")
      .then((r) => r.json())
      .then((data: { enabled: boolean }) => {
        if (!data.enabled) router.replace("/");
        else setChecking(false);
      })
      .catch(() => setChecking(false));
  }, [router]);

  useEffect(() => {
    if (step === "code") codeRef.current?.focus();
  }, [step]);

  async function handleEmailSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = email.trim().toLowerCase();
    if (!trimmed) return;
    setLoading(true);
    setError("");
    try {
      const res = await fetch("/api/auth/register/request-otp", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: trimmed }),
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
      const res = await fetch("/api/auth/register/verify-otp", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: email.trim().toLowerCase(), code }),
      });
      const data = (await res.json()) as { status?: string; message?: string; error?: string };
      if (!res.ok) throw new Error(data.error ?? "Invalid code");
      setDoneMessage(data.message ?? "Thanks! Your registration is pending approval.");
      setStep("done");
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

  const subtitle = () => {
    if (step === "email") return "Verify your email to request access";
    if (step === "code")  return `We sent a 6-digit code to ${email}`;
    return "Registration submitted";
  };

  return (
    <div className="relative flex h-screen flex-col items-center justify-center bg-[--color-surface]">
      <div className="w-full max-w-sm rounded-2xl border border-[--color-border] bg-[--color-surface-raised] p-8 shadow-sm">

        {/* Logo / title */}
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-black dark:bg-white">
            <svg className="h-6 w-6 text-white dark:text-black" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round"
                d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z" />
            </svg>
          </div>
          <h1 className="text-lg font-semibold">Register for SkyProton Agent System</h1>
          <p className="mt-1 text-sm text-[--color-muted]">{subtitle()}</p>
        </div>

        {/* ── Email step ─────────────────────────────────────────────────── */}
        {step === "email" && (
          <form onSubmit={handleEmailSubmit} className="space-y-4">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-[--color-muted]">Email address</label>
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
            {error && <p className="rounded-lg bg-red-500/10 px-3 py-2 text-xs text-red-400">{error}</p>}
            <button
              type="submit"
              disabled={loading || !email.trim()}
              className="w-full rounded-lg bg-black dark:bg-white px-4 py-2.5 text-sm font-medium text-white dark:text-black
                         transition-opacity hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "Sending…" : "Send code"}
            </button>
            <button
              type="button"
              onClick={() => router.push("/login")}
              className="w-full text-center text-xs text-[--color-muted] hover:text-current transition-colors"
            >
              Already have access? Sign in
            </button>
          </form>
        )}

        {/* ── OTP code step ───────────────────────────────────────────────── */}
        {step === "code" && (
          <form onSubmit={handleCodeSubmit} className="space-y-4">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-[--color-muted]">6-digit code</label>
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
            {error && <p className="rounded-lg bg-red-500/10 px-3 py-2 text-xs text-red-400">{error}</p>}
            <button
              type="submit"
              disabled={loading || code.length !== 6}
              className="w-full rounded-lg bg-black dark:bg-white px-4 py-2.5 text-sm font-medium text-white dark:text-black
                         transition-opacity hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? "Verifying…" : "Verify"}
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

        {/* ── Done ────────────────────────────────────────────────────────── */}
        {step === "done" && (
          <div className="space-y-4 text-center">
            <p className="text-sm">{doneMessage}</p>
            <button
              type="button"
              onClick={() => router.push("/login")}
              className="w-full rounded-lg bg-black dark:bg-white px-4 py-2.5 text-sm font-medium text-white dark:text-black
                         transition-opacity hover:opacity-90"
            >
              Go to sign in
            </button>
          </div>
        )}
      </div>
      <p className="absolute bottom-6 text-center text-xs text-[--color-muted]">
        &copy; {new Date().getFullYear()} SkyProton
      </p>
    </div>
  );
}
