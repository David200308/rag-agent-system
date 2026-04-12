import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

type Variant = "default" | "success" | "warning" | "danger" | "info";

interface BadgeProps {
  variant?: Variant;
  className?: string;
  children: ReactNode;
}

const styles: Record<Variant, string> = {
  default: "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300",
  success: "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300",
  warning: "bg-gray-200 text-gray-800 dark:bg-gray-700 dark:text-gray-200",
  danger:  "bg-gray-200 text-gray-800 dark:bg-gray-700 dark:text-gray-200",
  info:    "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300",
};

export function Badge({ variant = "default", className, children }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        styles[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}
