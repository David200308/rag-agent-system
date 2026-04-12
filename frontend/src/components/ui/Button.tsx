import { cn } from "@/lib/utils";
import type { ButtonHTMLAttributes, ReactNode } from "react";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "ghost" | "destructive";
  size?: "sm" | "md" | "icon";
  loading?: boolean;
  children: ReactNode;
}

export function Button({
  variant = "primary",
  size = "md",
  loading = false,
  className,
  disabled,
  children,
  ...props
}: ButtonProps) {
  const base =
    "inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[--color-brand-500] disabled:pointer-events-none disabled:opacity-50";

  const variants = {
    primary:     "bg-black text-white hover:bg-gray-800 active:bg-gray-900 dark:bg-white dark:text-black dark:hover:bg-gray-100",
    ghost:       "bg-transparent text-[--color-muted] hover:bg-[--color-surface-raised] hover:text-inherit",
    destructive: "bg-black text-white hover:bg-gray-800 dark:bg-white dark:text-black",
  };

  const sizes = {
    sm:   "h-8 px-3 text-sm",
    md:   "h-10 px-4 text-sm",
    icon: "h-9 w-9",
  };

  return (
    <button
      className={cn(base, variants[variant], sizes[size], className)}
      disabled={disabled ?? loading}
      {...props}
    >
      {loading ? (
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
      ) : (
        children
      )}
    </button>
  );
}
