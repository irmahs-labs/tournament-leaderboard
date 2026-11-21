import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import type { Observable } from "rxjs";
import { firstValueFrom } from "rxjs";

import { AdminAction } from "../admin/action";
import { CurrentSeason } from "../results/current-season";
import { splitRiotId } from "../results/stats";
import { LanguageService } from "../shared/language";
import { SessionService } from "../shared/session";
import type { ClaimState, PlayerSummary } from "./api";
import { MAX_NAME, MAX_TEXT, matchingPlayers } from "./api";

/**
 * Claim your profile: join the organisation as a player if needed, pick the player record admins
 * already enter scores for, add a note, and wait for an admin. The stats never move.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-claim",
  styleUrl: "./claim.css",
  templateUrl: "./claim.html",
})
export class ClaimPage {
  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly maxText = MAX_TEXT;
  protected readonly maxName = MAX_NAME;
  protected readonly splitRiotId = splitRiotId;

  /** The organisation's API, such as `/api/orgs/alsace-arena`. */
  readonly #org = computed(() => {
    const org = this.current.organization();

    return org ? `/api/orgs/${encodeURIComponent(org.slug)}` : undefined;
  });

  readonly #api = computed(() => {
    const org = this.#org();

    return org ? `${org}/claim` : undefined;
  });

  protected readonly state = httpResource<ClaimState>(() => this.#api());

  /** The players nobody has claimed, once they're a member who can still claim one. */
  readonly #unclaimed = httpResource<PlayerSummary[]>(() => {
    const api = this.#api();
    const state = this.state.hasValue() ? this.state.value() : null;

    return api &&
      state?.member &&
      !state.linked &&
      state.claim?.status !== "PENDING"
      ? `${api}/players`
      : undefined;
  });

  protected readonly search = signal("");
  /** The player they say is them, before they send the claim. */
  protected readonly picked = signal<PlayerSummary | null>(null);

  protected readonly players = computed(() =>
    matchingPlayers(
      this.#unclaimed.hasValue() ? this.#unclaimed.value() : [],
      this.search()
    )
  );

  protected readonly loadingPlayers = computed(
    () => this.#unclaimed.isLoading() && !this.#unclaimed.hasValue()
  );

  /** The Riot ID the account already has from another organisation, which a new profile takes. */
  protected readonly accountRiotId = computed(
    () => this.#session.me()?.riotId ?? null
  );

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      status === 409 ? "claim.conflict" : "claim.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);
  readonly #session = inject(SessionService);

  protected async join(): Promise<void> {
    await this.#change(
      () => this.#http.post<ClaimState>(`${this.#org()}/membership`, null),
      "claim.joined"
    );
    await this.#session.load();
  }

  protected async send(note: string): Promise<void> {
    const player = this.picked();

    if (!player) {
      return;
    }

    if (note.trim().length > MAX_TEXT) {
      this.action.refuse(
        this.#transloco.translate("claim.noteTooLong", { max: MAX_TEXT })
      );

      return;
    }

    await this.#change(
      (api) => this.#http.post<ClaimState>(api, { note, playerId: player.id }),
      "claim.sentDone"
    );
    this.picked.set(null);
  }

  /** Creates their player profile, linked at once: with the account's Riot ID when it has one. */
  protected async create(riotId: string, discord: string): Promise<void> {
    const org = this.#org();

    if (!org) {
      return;
    }

    if (!this.accountRiotId() && riotId.trim() === "") {
      this.action.refuse(this.#transloco.translate("claim.riotIdNeeded"));

      return;
    }

    await this.#change(
      () =>
        this.#http.post<ClaimState>(`${org}/players/me`, {
          discordUsername: discord.trim() === "" ? null : discord.trim(),
          riotId: riotId.trim() === "" ? null : riotId.trim(),
        }),
      "claim.created"
    );
    await this.#session.load();
  }

  protected async cancel(): Promise<void> {
    await this.#change(
      (api) => this.#http.delete<ClaimState>(api),
      "claim.cancelled"
    );
  }

  async #change(
    request: (api: string) => Observable<ClaimState>,
    done: string
  ): Promise<void> {
    const api = this.#api();

    if (!api) {
      return;
    }

    await this.action.run(async () => {
      this.state.set(await firstValueFrom(request(api)));
      this.#unclaimed.reload();

      return this.#transloco.translate(done);
    });
  }
}
