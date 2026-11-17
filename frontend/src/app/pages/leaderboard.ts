import { Component } from "@angular/core";
import { TranslocoDirective } from "@jsverse/transloco";

/** Holds the page's place in the shell until its own pull request fills it. */
@Component({
  imports: [TranslocoDirective],
  selector: "app-leaderboard",
  template: `
    <ng-container *transloco="let t">
      <h1 class="display">{{ t("leaderboard.title") }}</h1>
      <p class="muted">{{ t("common.comingSoon") }}</p>
    </ng-container>
  `,
})
export class LeaderboardPage {}
