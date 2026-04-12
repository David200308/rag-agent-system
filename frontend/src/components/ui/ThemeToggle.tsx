"use client";

import { Moon, Sun } from "lucide-react";
import { useTheme } from "@/hooks/useTheme";
import { Button } from "./Button";

export function ThemeToggle() {
  const { theme, toggle, mounted } = useTheme();

  // Render a placeholder until mounted to avoid hydration mismatch
  if (!mounted) {
    return <Button variant="ghost" size="icon" aria-label="Toggle theme" disabled><Sun className="h-4 w-4" /></Button>;
  }

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggle}
      aria-label={theme === "light" ? "Switch to dark mode" : "Switch to light mode"}
      title={theme === "light" ? "Dark mode" : "Light mode"}
    >
      {theme === "light" ? (
        <Moon className="h-4 w-4" />
      ) : (
        <Sun className="h-4 w-4" />
      )}
    </Button>
  );
}
