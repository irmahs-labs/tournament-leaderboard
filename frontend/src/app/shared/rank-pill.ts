import {
  booleanAttribute,
  Component,
  computed,
  inject,
  input,
} from "@angular/core";
import { TranslocoDirective } from "@jsverse/transloco";

import { rankText } from "./colour";
import { LanguageService } from "./language";

/** A player's Valorant rank, in the rank's colour made readable for the theme. */
@Component({
  host: {
    "[class.fixed]": "fixed()",
    "[style.--rank-dark]": "colours()?.dark",
    "[style.--rank-light]": "colours()?.light",
  },
  imports: [TranslocoDirective],
  selector: "app-rank-pill",
  styles: `
    :host {
      display: inline-block;
      box-sizing: border-box;
      padding: 3px 10px;
      background: var(--surface-2);
      border: 1px solid var(--line);
      color: var(--rank-dark, var(--text));
      font-size: 12px;
      font-weight: 700;
      white-space: nowrap;
    }

    :host-context([data-theme="light"]) {
      color: var(--rank-light, var(--text));
    }

    /* Same width in every row of a table, so the names line up. */
    :host(.fixed) {
      width: 116px;
      padding: 3px 6px;
      text-align: center;
    }
  `,
  // Inside *transloco so the name follows a change of language.
  template: `<ng-container *transloco="let t">{{
    language.rank(name())
  }}</ng-container>`,
})
export class RankPill {
  readonly name = input.required<string>();

  /** #RRGGBB, or nothing for the text colour. */
  readonly color = input<string | null>(null);

  /** Same width in every row, for tables. */
  readonly fixed = input(false, { transform: booleanAttribute });

  protected readonly colours = computed(() => rankText(this.color()));
  protected readonly language = inject(LanguageService);
}
