"use client";

import { useEffect } from "react";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    if (error.message?.includes("Failed to find Server Action")) {
      window.location.reload();
    }
  }, [error]);

  if (error.message?.includes("Failed to find Server Action")) {
    return (
      <html>
        <body />
      </html>
    );
  }

  return (
    <html>
      <body className="flex h-screen flex-col items-center justify-center gap-4 bg-white dark:bg-black">
        <p className="text-sm text-gray-500">Something went wrong.</p>
        <button
          onClick={reset}
          className="rounded-lg bg-black dark:bg-white px-4 py-2 text-sm font-medium text-white dark:text-black hover:opacity-90"
        >
          Try again
        </button>
      </body>
    </html>
  );
}
