import { HttpClient, httpResource } from "@angular/common/http";
import { Component, computed, inject, input, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import type { Observable } from "rxjs";
import { firstValueFrom } from "rxjs";

import { CurrentSeason } from "../../results/current-season";
import { splitRiotId } from "../../results/stats";
import { LanguageService } from "../../shared/language";
import type { SignUp, SignUps } from "../../sign-ups/api";
import { AdminAction } from "../action";
import type { AdminPlayer } from "../api";

/** The players not signed up yet whose Riot ID, Discord or an alias has the search, ignoring case. */
export const notSignedUp = (
  players: readonly AdminPlayer[],
  signUps: readonly SignUp[],
  search: string
): AdminPlayer[] => {
  const signedUp = new Set(signUps.map((each) => each.playerId));
  const wanted = search.trim().toLowerCase();

  return players.filter(
    (player) =>
      !signedUp.has(player.id) &&
      [
        player.riotId,
        player.discordUsername ?? "",
        ...player.aliases.map((alias) => alias.alias),
      ].some((name) => name.toLowerCase().includes(wanted))
  );
};

/**
 * Season sign-ups: open or close them; who signed up, in order; withdrawing someone; and signing
 * anyone up, players without an account too, whether sign-ups are open or not.
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

  protected async saveSettings(open: boolean): Promise<void> {
    await this.#change(
      (api) => this.#http.put<SignUps>(`${api}/signup`, { open }),
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
