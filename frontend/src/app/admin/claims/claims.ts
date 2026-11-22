import { HttpClient, httpResource } from "@angular/common/http";
import { Component, inject, signal } from "@angular/core";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import { firstValueFrom } from "rxjs";

import type { ClaimRow, ClaimStatus } from "../../claim/api";
import { MAX_TEXT } from "../../claim/api";
import { CurrentSeason } from "../../results/current-season";
import { LanguageService } from "../../shared/language";
import { RiotId, splitRiotId } from "../../shared/riot-id";
import { AdminAction } from "../action";

/** The tabs of the queue. */
export const CLAIM_TABS: readonly ClaimStatus[] = [
  "PENDING",
  "ACCEPTED",
  "DECLINED",
];

/** What a claim's player is called on its card, by the claim's state. */
export const CLAIM_LABELS: Readonly<Record<ClaimStatus, string>> = {
  ACCEPTED: "adminClaims.linkedTo",
  CANCELLED: "adminClaims.wanted",
  DECLINED: "adminClaims.wanted",
  PENDING: "adminClaims.wantsToBe",
};

/**
 * The admin Claims screen: players asking to be linked to a profile, with Accept and Decline (and a
 * reason the player sees). Accepting links their account; their stats stay where they are. An
 * admin's own claim waits for another admin, unless they're the only one.
 */
@Component({
  imports: [RiotId, TranslocoDirective],
  selector: "app-claims-admin",
  styleUrl: "./claims.css",
  templateUrl: "./claims.html",
})
export class ClaimsAdminPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly tabs = CLAIM_TABS;
  protected readonly claimLabels = CLAIM_LABELS;
  protected readonly maxText = MAX_TEXT;
  protected readonly splitRiotId = splitRiotId;

  protected readonly tab = signal<ClaimStatus>("PENDING");
  /** The claim being declined, whose reason is being typed. */
  protected readonly declining = signal<string | null>(null);

  protected readonly claims = httpResource<ClaimRow[]>(() => {
    const admin = this.current.adminApi();

    return admin
      ? { params: { status: this.tab() }, url: `${admin}/claims` }
      : undefined;
  });

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      {
        403: "adminClaims.ownClaim",
        409: "adminClaims.conflict",
      }[status] ?? "adminClaims.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);

  protected async accept(row: ClaimRow): Promise<void> {
    await this.#answer(row, "accept", null, "adminClaims.accepted");
  }

  protected async decline(row: ClaimRow, reason: string): Promise<void> {
    await this.#answer(row, "decline", { reason }, "adminClaims.declined");
    this.declining.set(null);
  }

  async #answer(
    row: ClaimRow,
    verb: "accept" | "decline",
    body: { reason: string } | null,
    done: string
  ): Promise<void> {
    const admin = this.current.adminApi();

    if (!admin) {
      return;
    }

    await this.action.run(async () => {
      await firstValueFrom(
        this.#http.post(`${admin}/claims/${row.id}/${verb}`, body)
      );
      this.claims.reload();

      return this.#transloco.translate(done, {
        name: row.name ?? row.email ?? "",
        player: splitRiotId(row.player.riotId).name,
      });
    });
  }
}
