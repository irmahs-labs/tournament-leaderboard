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
    </ng-container>
  `,
})
export class NotFoundPage {}
