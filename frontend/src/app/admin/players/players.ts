import {
  HttpClient,
  HttpErrorResponse,
  httpResource,
} from "@angular/common/http";
import { Component, computed, inject, signal } from "@angular/core";
import { TranslocoDirective, TranslocoService } from "@jsverse/transloco";
import type { Observable } from "rxjs";
import { firstValueFrom } from "rxjs";

import { CurrentSeason } from "../../results/current-season";
import { splitRiotId } from "../../results/stats";
import { RankPill } from "../../shared/rank-pill";
import type { AdminPlayer, Alias, PlayerInput, RankOption } from "../api";

/** A player's aliases as one line: "Bram, bramblebush". */
export const aliasesText = (aliases: readonly Alias[]): string =>
  aliases.map((alias) => alias.alias).join(", ");

/**
 * The aliases typed in a line, separated by commas: each once, ignoring case, and never the Riot
 * ID itself. A name the player already had keeps its kind, so previous Riot IDs stay marked as
 * such; a new one is a nickname.
 */
export const aliasesFrom = (
  text: string,
  previous: readonly Alias[],
  riotId: string
): Alias[] => {
  const kinds = new Map(
    previous.map((alias) => [alias.alias.toLowerCase(), alias.kind])
  );

  const seen = new Set([riotId.trim().toLowerCase()]);
  const aliases: Alias[] = [];

  for (const part of text.split(",")) {
    const alias = part.trim();
    const key = alias.toLowerCase();

    if (alias && !seen.has(key)) {
      seen.add(key);
      aliases.push({ alias, kind: kinds.get(key) ?? "NICKNAME" });
    }
  }

  return aliases;
};

/** What a form or a row typed, before it becomes a request. */
export interface PlayerFields {
  riotId: string;
  discord: string;
  aliases: string;
  rank: string;
  /** The Google email typed in advance; empty for none. */
  email: string;
}

/** The request for a player's fields; `previous` is the player being edited, if any. */
export const inputFrom = (
  fields: PlayerFields,
  previous: AdminPlayer | null
): PlayerInput => {
  const riotId = fields.riotId.trim();
  const rankId = Number(fields.rank);

  return {
    aliases: aliasesFrom(fields.aliases, previous?.aliases ?? [], riotId),
    discordUsername: fields.discord.trim() || null,
    expectedEmail: fields.email.trim() || null,
    rankId: fields.rank && Number.isInteger(rankId) ? rankId : null,
    riotId,
  };
};

/** The message after an add or a save: what happened, for a status line. */
interface Outcome {
  ok: boolean;
  text: string;
}

/**
 * The organisation's players: add one, search them, and edit a player's Riot ID, Discord
 * username, aliases and rank. Players are never deleted, so past results stay intact.
 */
@Component({
  imports: [RankPill, TranslocoDirective],
  selector: "app-players-admin",
  styleUrl: "./players.css",
  templateUrl: "./players.html",
})
export class PlayersAdminPage {
  protected readonly current = inject(CurrentSeason);

  protected readonly query = signal("");

  protected readonly players = httpResource<AdminPlayer[]>(() => {
    const api = this.#api();

    return api ? { params: { q: this.query() }, url: api } : undefined;
  });

  readonly #ranks = httpResource<RankOption[]>(() => "/api/ranks");

  /** The ranks to pick from; none until they arrive. */
  protected readonly ranks = computed(() =>
    this.#ranks.hasValue() ? this.#ranks.value() : []
  );

  protected readonly count = computed(() =>
    this.players.hasValue() ? this.players.value().length : 0
  );

  /** The player whose row is open for editing. */
  protected readonly editing = signal<string | null>(null);

  protected readonly busy = signal(false);
  protected readonly outcome = signal<Outcome | null>(null);

  protected readonly splitRiotId = splitRiotId;
  protected readonly aliasesText = aliasesText;

  readonly #http = inject(HttpClient);
  readonly #transloco = inject(TranslocoService);

  readonly #api = computed(() => {
    const admin = this.current.adminApi();

    return admin ? `${admin}/players` : undefined;
  });

  protected search(value: string): void {
    this.query.set(value.trim());
  }

  /** Adds the player the form describes; the form empties once it's done. */
  protected async add(event: Event, form: HTMLFormElement): Promise<void> {
    event.preventDefault();
    const data = new FormData(form);

    const field = (name: string): string => data.get(name)?.toString() ?? "";

    const input = inputFrom(
      {
        aliases: field("aliases"),
        discord: field("discord"),
        email: field("email"),
        rank: field("rank"),
        riotId: field("riotId"),
      },
      null
    );

    const added = await this.#send(
      (api) => this.#http.post<AdminPlayer>(api, input),
      "adminPlayers.added"
    );

    if (added) {
      form.reset();
    }
  }

  /** Saves an edited row and closes it. */
  protected async save(
    player: AdminPlayer,
    fields: PlayerFields
  ): Promise<void> {
    const saved = await this.#send(
      (api) =>
        this.#http.put<AdminPlayer>(
          `${api}/${encodeURIComponent(player.id)}`,
          inputFrom(fields, player)
        ),
      "adminPlayers.saved"
    );

    if (saved) {
      this.editing.set(null);
    }
  }

  async #send(
    request: (api: string) => Observable<AdminPlayer>,
    done: string
  ): Promise<boolean> {
    const api = this.#api();

    if (!api || this.busy()) {
      return false;
    }

    this.busy.set(true);
    this.outcome.set(null);

    try {
      const player = await firstValueFrom(request(api));
      this.outcome.set({
        ok: true,
        text: this.#transloco.translate(done, {
          name: splitRiotId(player.riotId).name,
        }),
      });
      this.players.reload();

      return true;
    } catch (error) {
      const status = error instanceof HttpErrorResponse ? error.status : 0;
      this.outcome.set({ ok: false, text: this.#failure(status) });

      return false;
    } finally {
      this.busy.set(false);
    }
  }

  /** Why a change failed, from the answer's status, in the language on screen. */
  #failure(status: number): string {
    if (status === 409) {
      return this.#transloco.translate("adminPlayers.taken");
    }

    if (status === 400) {
      return this.#transloco.translate("adminPlayers.invalid");
    }

    return this.#transloco.translate("adminPlayers.failed");
  }
}
