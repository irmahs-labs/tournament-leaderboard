import { Component } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective } from "@jsverse/transloco";

/** Any unknown address, and an organisation that doesn't exist or is suspended. */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-not-found",
  styleUrl: "./not-found.css",
  template: `
    <ng-container *transloco="let t">
      <div class="copy">
        <span class="code" aria-hidden="true">404</span>
        <h1 class="display">{{ t("notFound.title") }}</h1>
        <p class="lede">{{ t("notFound.body") }}</p>
        <div>
          <a class="button" routerLink="/">{{ t("notFound.home") }}</a>
        </div>
      </div>
      <!-- The site's rank-up mark, as in the header, drawn large. -->
      <svg
        class="mark"
        viewBox="0 0 40 40"
        fill="none"
        stroke-width="2"
        aria-hidden="true"
      >
        <path class="badge" d="M20 2l16 9v18l-16 9-16-9V11z" />
        <path class="up" d="M12 24l8-8 8 8" stroke-width="2.5" />
        <path class="up below" d="M12 31l8-8 8 8" stroke-width="2.5" />
      </svg>
    </ng-container>
  `,
})
export class NotFoundPage {}
