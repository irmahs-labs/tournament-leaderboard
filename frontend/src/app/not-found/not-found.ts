import { Component, computed, inject } from "@angular/core";
import { toSignal } from "@angular/core/rxjs-interop";
import { ActivatedRoute, RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

import { SessionService } from "../shared/session";

/**
 * Any unknown address, and an organisation that doesn't exist or is suspended. Its members are
 * told it's suspended rather than missing.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-not-found",
  styleUrl: "./not-found.css",
  template: `
    <ng-container *transloco="let t">
      <div class="copy">
        @if (suspended(); as org) {
          <h1 class="display">
            {{ t("notFound.suspendedTitle", { org: org.name }) }}
          </h1>
          <p class="lede">
            {{ t("notFound.suspendedBody", { org: org.name }) }}
          </p>
        } @else {
          <span class="code" aria-hidden="true">404</span>
          <h1 class="display">{{ t("notFound.title") }}</h1>
          <p class="lede">{{ t("notFound.body") }}</p>
        }
        <div>
          <a class="button" routerLink="/">{{ t("notFound.home") }}</a>
        </div>
      </div>
    </ng-container>
  `,
})
export class NotFoundPage {
  readonly #session = inject(SessionService);
  readonly #query = toSignal(inject(ActivatedRoute).queryParamMap);

  /** The suspended organisation the address was about, when the signed-in person belongs to it. */
  protected readonly suspended = computed(() => {
    const slug = this.#query()?.get("suspended");

    return slug
      ? (this.#session
          .me()
          ?.suspended.find((membership) => membership.slug === slug) ?? null)
      : null;
  });
}
