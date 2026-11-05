import { defineConfig } from "oxlint";
import angular from "ultracite/oxlint/angular";
import antiSlop from "ultracite/oxlint/anti-slop";
import core from "ultracite/oxlint/core";
import vitest from "ultracite/oxlint/vitest";

// The irmahs-labs/template config, plus the Angular and Vitest presets.
export default defineConfig({
  extends: [core, angular, vitest, antiSlop],
  ignorePatterns: core.ignorePatterns,
  rules: {
    // Angular components and services are classes that only carry a decorator.
    "typescript/no-extraneous-class": ["error", { allowWithDecorator: true }],
  },
});
