import {
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
} from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import { Title } from "@angular/platform-browser";
import { Router } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";

import type { Language } from "../shared/language";
import { LanguageService } from "../shared/language";
import type { LegalPageId } from "./legal-content";
import { LEGAL, UPDATED } from "./legal-content";

/** One of the legal pages, in the language on screen; which one comes from the route's data. */
@Component({
  imports: [TranslocoDirective],
  selector: "app-legal-page",
  styleUrl: "./legal-page.css",
  templateUrl: "./legal-page.html",
})
export class LegalPage {
  readonly page = input.required<LegalPageId>();

  readonly #language = inject(LanguageService);
  readonly #lang = toSignal(inject(TranslocoService).langChanges$, {
    initialValue: this.#language.active,
  });

  protected readonly lang = computed((): Language =>
    this.#lang() === "fr" ? "fr" : "en"
  );
  protected readonly doc = computed(() => LEGAL[this.lang()][this.page()]);
  protected readonly updatedOn = UPDATED;

  readonly #router = inject(Router);
  protected readonly updated = computed(() =>
    new Intl.DateTimeFormat(this.lang(), {
      dateStyle: "long",
      timeZone: "UTC",
    }).format(new Date(`${UPDATED}T00:00:00Z`))
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

  /** Opens the site's own pages inside the app; other links leave as usual. */
  protected follow(event: MouseEvent, href: string): void {
    if (
      href.startsWith("/") &&
      !event.ctrlKey &&
      !event.metaKey &&
      !event.shiftKey
    ) {
      event.preventDefault();
      void this.#router.navigateByUrl(href);
    }
  }
}
