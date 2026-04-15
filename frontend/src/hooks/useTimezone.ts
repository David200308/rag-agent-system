"use client";

import { useEffect, useRef, useState } from "react";

const STORAGE_KEY = "rag-timezone";

function browserTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

async function fetchRemoteTimezone(): Promise<string | null> {
  try {
    const res = await fetch("/api/agent/user/preferences");
    if (!res.ok) return null;
    const data = (await res.json()) as { timezone?: string };
    return data.timezone ?? null;
  } catch {
    return null;
  }
}

async function saveRemoteTimezone(tz: string): Promise<void> {
  try {
    await fetch("/api/agent/user/preferences", {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ timezone: tz }),
    });
  } catch {
    // ignore — localStorage is the fallback
  }
}

export function useTimezone() {
  const [timezone, setTimezoneState] = useState<string>("UTC");
  const [mounted, setMounted] = useState(false);
  // Prevent saving back to the backend the value we just fetched from it
  const skipNextRemoteSave = useRef(false);

  // 1. On mount: read localStorage immediately, then sync from backend
  useEffect(() => {
    const stored = localStorage.getItem(STORAGE_KEY) ?? browserTimezone();
    setTimezoneState(stored);
    setMounted(true);

    // Async: pull from backend; backend wins over localStorage
    fetchRemoteTimezone().then((remote) => {
      if (remote && remote !== stored) {
        skipNextRemoteSave.current = true;
        setTimezoneState(remote);
        localStorage.setItem(STORAGE_KEY, remote);
      }
    });
  }, []);

  // 2. On change: save to localStorage; save to backend unless we just fetched it
  const setTimezone = (tz: string) => {
    setTimezoneState(tz);
    localStorage.setItem(STORAGE_KEY, tz);
    if (skipNextRemoteSave.current) {
      skipNextRemoteSave.current = false;
    } else {
      saveRemoteTimezone(tz);
    }
  };

  return { timezone, setTimezone, mounted };
}

/** Read the saved timezone synchronously (for non-hook contexts). */
export function getStoredTimezone(): string {
  if (typeof window === "undefined") return "UTC";
  return localStorage.getItem(STORAGE_KEY) ?? browserTimezone();
}
