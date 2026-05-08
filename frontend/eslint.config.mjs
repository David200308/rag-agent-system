import js from "@eslint/js";
import tseslint from "typescript-eslint";
import { dirname } from "path";
import { fileURLToPath } from "url";
import nextCoreWebVitals from "eslint-config-next/core-web-vitals";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

export default tseslint.config(
  js.configs.recommended,

  // Basic TypeScript rules (no type-info required — avoids TypeScript 6 / ts-eslint 8.x peer conflict)
  ...tseslint.configs.recommended,

  // Next.js flat config (eslint-config-next 16.x exports ESLint 9 flat config natively)
  ...nextCoreWebVitals,

  // TypeScript-specific rules scoped to TS files with type-aware parser options
  {
    files: ["**/*.ts", "**/*.tsx"],
    languageOptions: {
      parserOptions: {
        project: "./tsconfig.json",
        tsconfigRootDir: __dirname,
      },
    },
    rules: {
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      "@typescript-eslint/consistent-type-imports": ["error", { prefer: "type-imports" }],
      "@typescript-eslint/no-explicit-any": "warn",
    },
  },

  // Global rules
  {
    rules: {
      "no-console": ["warn", { allow: ["warn", "error"] }],
      // react-hooks v5 strict rules — disabled: these patterns are intentional in this codebase
      "react-hooks/set-state-in-effect": "off",
      "react-hooks/refs": "off",
    },
  },

  {
    ignores: [".next/**", "node_modules/**", "dist/**"],
  },
);
