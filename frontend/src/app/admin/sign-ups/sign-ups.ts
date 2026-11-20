import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject, input, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import type { Observable } from "rxjs";
import { firstValueFrom } from "rxjs";

import { matchingPlayers } from "../../claim/api";
import { CurrentSeason } from "../../results/current-season";
import { splitRiotId } from "../../results/stats";
import { LanguageService } from "../../shared/language";
import type { SignUp, SignUps } from "../../sign-ups/api";
import { AdminAction } from "../action";
import type { AdminPlayer } from "../api";

/** The players an admin can still sign up: everyone not signed up, matching the search. */
export const notSignedUp = (
  players: readonly AdminPlayer[],
  signUps: readonly SignUp[],
  search: string
): AdminPlayer[] => {
  const signedUp = new Set(
    signUps.flatMap((each) =>
      each.status === "REGISTERED" ? [each.playerId] : []
    )
  );

  const left = players.filter((player) => !signedUp.has(player.id));

  const found = new Set(
    matchingPlayers(
      left.map((player) => ({
        aliases: player.aliases.map((alias) => alias.alias),
        discordUsername: player.discordUsername,
        games: 0,
        id: player.id,
        rank: null,
        riotId: player.riotId,
      })),
      search
    ).map((player) => player.id)
  );

  return left.filter((player) => found.has(player.id));
};

/**
 * Season sign-ups: open or close them, with a last day; who signed up, in order; taking someone
 * off; and signing anyone up, players without an account too, whether sign-ups are open or not.
 */
@Component({
  imports: [RouterLink, TranslocoDirective],
  selector: "app-sign-ups-admin",
  styleUrl: "./sign-ups.css",
  templateUrl: "./sign-ups.html",
})
export class SignUpsAdminPage {
  /** From the address: /org/:slug/admin/seasons/:season/sign-ups. */
  readonly season = input.required<string>();

  protected readonly current = inject(CurrentSeason);
  protected readonly language = inject(LanguageService);
  protected readonly splitRiotId = splitRiotId;

  readonly #api = computed(() => {
    const admin = this.current.adminApi();

    return admin
      ? `${admin}/seasons/${encodeURIComponent(this.season())}`
      : undefined;
  });

  protected readonly signUps = httpResource<SignUps>(() => {
    const api = this.#api();

    return api ? `${api}/registrations` : undefined;
  });

  readonly #players = httpResource<AdminPlayer[]>(() => {
    const admin = this.current.adminApi();

    return admin ? `${admin}/players` : undefined;
  });

  protected readonly search = signal("");

  protected readonly signedUp = computed(() =>
    this.signUps.hasValue()
      ? this.signUps
          .value()
          .registrations.filter((each) => each.status === "REGISTERED")
      : []
  );

  protected readonly gone = computed(() =>
    this.signUps.hasValue()
      ? this.signUps
          .value()
          .registrations.filter((each) => each.status !== "REGISTERED")
      : []
  );

  /** Whom the admin can sign up, once they search. */
  protected readonly candidates = computed(() =>
    this.search().trim() === "" ||
    !this.#players.hasValue() ||
    !this.signUps.hasValue()
      ? []
      : notSignedUp(
          this.#players.value(),
          this.signUps.value().registrations,
          this.search()
        ).slice(0, 8)
  );

  protected readonly action = new AdminAction((status) =>
    this.#transloco.translate(
      status === 409 ? "adminSignUps.conflict" : "adminSignUps.failed"
    )
  );

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);

  protected async saveSettings(open: boolean, closesOn: string): Promise<void> {
    await this.#change(
      (api) =>
        this.#http.put<SignUps>(`${api}/signup`, {
          closesOn: closesOn === "" ? null : closesOn,
          open,
        }),
      "adminSignUps.settingsSaved",
      ""
    );
  }

  protected async add(player: { id: string; riotId: string }): Promise<void> {
    await this.#change(
      (api) =>
        this.#http.post<SignUps>(
          `${api}/registrations/${encodeURIComponent(player.id)}`,
          null
        ),
      "adminSignUps.added",
      player.riotId
    );
    this.search.set("");
  }

  protected async remove(row: SignUp): Promise<void> {
    await this.#change(
      (api) =>
        this.#http.delete<SignUps>(
          `${api}/registrations/${encodeURIComponent(row.playerId)}`
        ),
      "adminSignUps.removed",
      row.riotId ?? ""
    );
  }

  async #change(
    request: (api: string) => Observable<SignUps>,
    done: string,
    riotId: string
  ): Promise<void> {
    const api = this.#api();

    if (!api) {
      return;
    }

    await this.action.run(async () => {
      this.signUps.set(await firstValueFrom(request(api)));

      return this.#transloco.translate(done, {
        name: splitRiotId(riotId).name,
      });
    });
  }
}
