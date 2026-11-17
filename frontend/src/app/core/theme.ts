import { DOCUMENT } from "@angular/common";
import { computed, effect, Injectable, inject, signal } from "@angular/core";

import { accentOrDefault, mix, onAccent, readableAccent } from "./colour";

export type Theme = "dark" | "light";

const STORAGE_KEY = "theme";

/** The two palettes' surfaces and text, which the accent's derived colours are measured against. */
const PALETTES: Record<Theme, { surface: string; text: string }> = {
  dark: { surface: "#161D27", text: "#ECE8E1" },
  light: { surface: "#F7F5F0", text: "#0F141B" },
};

const readStored = (storage: Storage | undefined): Theme | null => {
  try {
    const value = storage?.getItem(STORAGE_KEY);

    return value === "dark" || value === "light" ? value : null;
  } catch {
    return null;
  }
};

/**
 * Dark or light, and the organisation's accent. Follows the system setting until the visitor
 * picks one, then remembers their pick in this browser.
 */
@Injectable({ providedIn: "root" })
export class ThemeService {
  readonly #document = inject(DOCUMENT);
  readonly #window = this.#document.defaultView;
  readonly #chosen = signal<Theme | null>(readStored(this.#storage()));
  readonly #systemDark = signal(this.#prefersDark());
  readonly #accent = signal<string | null>(null);

  /** The theme on screen. */
  readonly theme = computed<Theme>(
    () => this.#chosen() ?? (this.#systemDark() ? "dark" : "light")
  );

  constructor() {
    this.#window
      ?.matchMedia?.("(prefers-color-scheme: dark)")
      .addEventListener("change", (event) =>
        this.#systemDark.set(event.matches)
      );
    effect(() => {
      this.#document.documentElement.dataset["theme"] = this.theme();
    });
    effect(() => this.#applyAccent(accentOrDefault(this.#accent())));
  }

  toggle(): void {
    const next: Theme = this.theme() === "dark" ? "light" : "dark";
    this.#chosen.set(next);

    try {
      this.#storage()?.setItem(STORAGE_KEY, next);
    } catch {
      // Storage can be blocked; the pick then lasts until the page is closed.
    }
  }

  /** The organisation's accent colour, or nothing for the template's red. */
  setAccent(accent: string | null): void {
    this.#accent.set(accent);
  }

  #applyAccent(accent: string): void {
    const { style } = this.#document.documentElement;
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

  #prefersDark(): boolean {
    return (
      this.#window?.matchMedia?.("(prefers-color-scheme: dark)").matches ?? true
    );
  }

  #storage(): Storage | undefined {
    try {
      return this.#document.defaultView?.localStorage;
    } catch {
      return undefined;
    }
  }
}
