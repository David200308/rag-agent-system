"use client";

import { useEffect, useState } from "react";

type Theme = "light" | "dark";

const STORAGE_KEY = "rag-theme";

function getInitialTheme(): Theme {
  if (typeof window === "undefined") return "light";
  const stored = localStorage.getItem(STORAGE_KEY) as Theme | null;
  if (stored === "light" || stored === "dark") return stored;
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function useTheme() {
  const [theme, setThemeState] = useState<Theme>("light");
  const [mounted, setMounted] = useState(false);

  // Read from storage after mount to avoid SSR mismatch
  useEffect(() => {
    setThemeState(getInitialTheme());
    setMounted(true);
  }, []);

  // Apply to <html data-theme="..."> whenever theme changes
  useEffect(() => {
    if (!mounted) return;
    document.documentElement.dataset["theme"] = theme;
    localStorage.setItem(STORAGE_KEY, theme);
  }, [theme, mounted]);

  const toggle = () => setThemeState((t) => (t === "light" ? "dark" : "light"));

  return { theme, toggle, mounted };
}
