// ESLint v10 flat config — replaces .eslintrc.json
// typescript-eslint v8 fully replaces the deprecated TSLint.
import js from "@eslint/js";
import tseslint from "typescript-eslint";
import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({ baseDirectory: __dirname });

export default tseslint.config(
  // Base JS recommended rules
  js.configs.recommended,

  // TypeScript strict rules (replaces TSLint's type-checking rules)
  ...tseslint.configs.strictTypeChecked,
  ...tseslint.configs.stylisticTypeChecked,

  // Next.js specific rules via compat shim
  ...compat.extends("next/core-web-vitals"),

  // Project-wide TS parser options
  {
    languageOptions: {
      parserOptions: {
        project: "./tsconfig.json",
        tsconfigRootDir: __dirname,
      },
    },
  },

  // Custom rule overrides
  {
    rules: {
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      "@typescript-eslint/consistent-type-imports": ["error", { prefer: "type-imports" }],
      "@typescript-eslint/no-explicit-any": "warn",
      "@typescript-eslint/no-floating-promises": "error",
      "no-console": ["warn", { allow: ["warn", "error"] }],
    },
  },

  // Ignore build artifacts
  {
    ignores: [".next/**", "node_modules/**", "dist/**"],
  },
);
