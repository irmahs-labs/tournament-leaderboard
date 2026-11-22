import { DOCUMENT } from "@angular/common";
import { Injectable, inject } from "@angular/core";
import { TranslocoService } from "@jsverse/transloco";

import { readStored, store } from "./storage";

export type Language = "en" | "fr";

export const LANGUAGES: readonly Language[] = ["en", "fr"];

const STORAGE_KEY = "lang";

const isLanguage = (value: string | null | undefined): value is Language =>
  value === "en" || value === "fr";

/** The language the visitor picked in this browser, if any. */
const picked = (): Language | null => {
  const value = readStored(STORAGE_KEY);

  return isLanguage(value) ? value : null;
};

/** Where Transloco starts: the visitor's pick, English otherwise. */
export const startingLanguage = (): Language => picked() ?? "en";

/** A number in the language's style, with a dash for nothing: "5,749" in English, "5 749" in French. */
export const formatNumber = (
  value: number | null | undefined,
  lang: string,
  decimals = 0
): string =>
  value === null || value === undefined
    ? "–"
    : new Intl.NumberFormat(lang, {
        maximumFractionDigits: decimals,
        minimumFractionDigits: decimals,
      }).format(value);

/** Valorant's tiers as the French game names them; the others keep their English name. */
const FRENCH_TIERS = {
  Diamond: "Diamant",
  Gold: "Or",
  Immortal: "Immortel",
  Iron: "Fer",
  Platinum: "Platine",
  Silver: "Argent",
} satisfies Record<string, string>;

/** A Valorant rank's name as the game shows it in this language: "Immortal 1" is "Immortel 1". */
export const rankIn = (name: string, lang: Language): string => {
  if (lang !== "fr") {
    return name;
  }

  const [tier = "", ...rest] = name.split(" ");

  const french = Object.entries(FRENCH_TIERS).find(
    ([english]) => english === tier
  )?.[1];

  return [french ?? tier, ...rest].join(" ");
};

/**
 * English or French. A visitor's own pick wins; otherwise each organisation's default language
 * applies. Also formats numbers, dates, plurals and rank names in the language on screen.
 */
@Injectable({ providedIn: "root" })
export class LanguageService {
  readonly #transloco = inject(TranslocoService);

  constructor() {
    const root = inject(DOCUMENT).documentElement;
    this.#transloco.langChanges$.subscribe((lang) => {
      root.lang = lang;
    });
  }

  get active(): Language {
    const lang = this.#transloco.getActiveLang();

    return isLanguage(lang) ? lang : "en";
  }

  /** The visitor's pick, remembered in this browser. */
  choose(lang: Language): void {
    this.#transloco.setActiveLang(lang);
    store(STORAGE_KEY, lang);
  }

  /** Switches to the organisation's language, unless the visitor picked one. */
  useDefault(lang: string | null | undefined): void {
    if (!picked() && isLanguage(lang)) {
      this.#transloco.setActiveLang(lang);
    }
  }

  number(value: number | null | undefined, decimals = 0): string {
    return formatNumber(value, this.active, decimals);
  }

  /**
   * Points, with decimals only when they have some: "422", "85.5", "130.7". Totals show one
   * decimal; what a stat is worth, which an admin sets to the hundredth, shows two.
   */
  points(value: number | null | undefined, decimals = 1): string {
    return value === null || value === undefined
      ? "–"
      : new Intl.NumberFormat(this.active, {
          maximumFractionDigits: decimals,
        }).format(value);
  }

  /** A date as a short day and month, such as "Nov 20" or "20 nov.". */
  day(date: string): string {
    return new Intl.DateTimeFormat(this.active, {
      day: "numeric",
      month: "short",
      timeZone: "UTC",
    }).format(new Date(`${date}T00:00:00Z`));
  }

  /** A moment in the visitor's own time: "Thu 20 Nov, 21:14" or "jeu. 20 nov., 21:14". */
  moment(instant: string): string {
    return new Intl.DateTimeFormat(this.active, {
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      month: "short",
      weekday: "short",
    }).format(new Date(instant));
  }

  /** A time of day, such as "8:30 PM" or "20:30", from the API's "20:30:00". */
  time(value: string): string {
    return new Intl.DateTimeFormat(this.active, {
      hour: "numeric",
      minute: "2-digit",
      timeZone: "UTC",
    }).format(new Date(`1970-01-01T${value}Z`));
  }

  /** A date as a short month and year, such as "Jan 2026" or "janv. 2026". */
  month(date: string): string {
    return new Intl.DateTimeFormat(this.active, {
      month: "short",
      timeZone: "UTC",
      year: "numeric",
    }).format(new Date(`${date}T00:00:00Z`));
  }

  /** A Valorant rank's name as the game shows it in the language on screen: "Immortel 1" in French. */
  rank(name: string): string {
    return rankIn(name, this.active);
  }

  /** The key for a count in the language's plural form, such as `leaderboard.wins.one`. */
  plural(key: string, count: number): string {
    return new Intl.PluralRules(this.active).select(count) === "one"
      ? `${key}.one`
      : `${key}.other`;
  }
}
