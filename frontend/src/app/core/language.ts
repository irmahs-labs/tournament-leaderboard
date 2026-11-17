import { DOCUMENT } from "@angular/common";
import { Injectable, inject } from "@angular/core";
import { TranslocoService } from "@jsverse/transloco";

export type Language = "en" | "fr";

export const LANGUAGES: readonly Language[] = ["en", "fr"];

const STORAGE_KEY = "lang";

const isLanguage = (value: string | null | undefined): value is Language =>
  value === "en" || value === "fr";

/**
 * English or French. A visitor's own pick wins; otherwise each organisation's default language
 * applies.
 */
@Injectable({ providedIn: "root" })
export class LanguageService {
  readonly #transloco = inject(TranslocoService);
  readonly #document = inject(DOCUMENT);

  constructor() {
    this.#transloco.langChanges$.subscribe((lang) => {
      this.#document.documentElement.lang = lang;
    });
    const stored = this.#read();

    if (stored) {
      this.#transloco.setActiveLang(stored);
    }
  }

  get active(): Language {
    const lang = this.#transloco.getActiveLang();

    return isLanguage(lang) ? lang : "en";
  }

  /** The visitor's pick, remembered in this browser. */
  choose(lang: Language): void {
    this.#transloco.setActiveLang(lang);

    try {
      this.#document.defaultView?.localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // Storage can be blocked; the pick then lasts until the page is closed.
    }
  }

  /** Switches to the organisation's language, unless the visitor picked one. */
  useDefault(lang: string | null | undefined): void {
    if (!this.#read() && isLanguage(lang)) {
      this.#transloco.setActiveLang(lang);
    }
  }

  #read(): Language | null {
    try {
      const value =
        this.#document.defaultView?.localStorage.getItem(STORAGE_KEY);

      return isLanguage(value) ? value : null;
    } catch {
      return null;
    }
  }
}
