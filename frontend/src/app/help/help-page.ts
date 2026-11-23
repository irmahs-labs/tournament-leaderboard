import { Component, DestroyRef, computed, effect, inject } from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import { Title } from "@angular/platform-browser";
import { TranslocoService } from "@jsverse/transloco";

import type { Language } from "../shared/language";
import { LanguageService } from "../shared/language";
import { HELP } from "./help-content";

/** Help: questions and answers for players, admins and one's account, in the language on screen. */
@Component({
  selector: "app-help-page",
  styleUrl: "./help-page.css",
  templateUrl: "./help-page.html",
})
export class HelpPage {
  readonly #language = inject(LanguageService);
  readonly #lang = toSignal(inject(TranslocoService).langChanges$, {
    initialValue: this.#language.active,
  });

  protected readonly doc = computed(
    () => HELP[(this.#lang() === "fr" ? "fr" : "en") satisfies Language]
  );

  constructor() {
    const title = inject(Title);
    const platform = title.getTitle();
    effect(() =>
      title.setTitle(`${this.doc().title} · Tournament Leaderboard`)
    );
    // The shell sets its title once, so put it back for the next page.
    inject(DestroyRef).onDestroy(() => title.setTitle(platform));
  }
}
