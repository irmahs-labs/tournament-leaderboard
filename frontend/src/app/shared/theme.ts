import { DOCUMENT } from "@angular/common";
import { computed, effect, Injectable, inject, signal } from "@angular/core";

import type { Theme } from "./colour";
import {
  accentOrDefault,
  mix,
  onAccent,
  PALETTES,
  readableAccent,
} from "./colour";
import { readStored, store } from "./storage";

export type { Theme } from "./colour";

const STORAGE_KEY = "theme";

const picked = (): Theme | null => {
  const value = readStored(STORAGE_KEY);

  return value === "dark" || value === "light" ? value : null;
};

/**
 * Dark or light, and the organisation's accent. Follows the system setting until the visitor
 * picks one, then remembers their pick in this browser.
 */
@Injectable({ providedIn: "root" })
export class ThemeService {
  readonly #root = inject(DOCUMENT).documentElement;
  readonly #system = inject(DOCUMENT).defaultView?.matchMedia?.(
    "(prefers-color-scheme: dark)"
  );
  readonly #chosen = signal<Theme | null>(picked());
  readonly #systemDark = signal(this.#system?.matches ?? true);
  readonly #accent = signal<string | null>(null);

  /** The theme on screen. */
  readonly theme = computed<Theme>(
    () => this.#chosen() ?? (this.#systemDark() ? "dark" : "light")
  );

  constructor() {
    this.#system?.addEventListener("change", (event) =>
      this.#systemDark.set(event.matches)
    );
    effect(() => {
      this.#root.dataset["theme"] = this.theme();
    });
    effect(() => this.#applyAccent(accentOrDefault(this.#accent())));
  }

  toggle(): void {
    const next: Theme = this.theme() === "dark" ? "light" : "dark";
    this.#chosen.set(next);
    store(STORAGE_KEY, next);
  }

  /** The organisation's accent colour, or nothing for the template's red. */
  setAccent(accent: string | null): void {
    this.#accent.set(accent);
  }

  #applyAccent(accent: string): void {
    const { style } = this.#root;
    style.setProperty("--accent", accent);
    style.setProperty("--on-accent", onAccent(accent));

    for (const theme of ["dark", "light"] as const) {
      const { surface, text } = PALETTES[theme];
      style.setProperty(
        `--accent-text-${theme}`,
        readableAccent(accent, surface, text)
      );
      style.setProperty(`--accent-soft-${theme}`, mix(accent, surface, 0.86));
    }
  }
}
