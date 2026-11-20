import { provideHttpClient } from "@angular/common/http";
import {
  HttpTestingController,
  provideHttpClientTesting,
} from "@angular/common/http/testing";
import type { EnvironmentProviders, Provider } from "@angular/core";
import { importProvidersFrom } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { provideRouter, withComponentInputBinding } from "@angular/router";
import { RouterTestingHarness } from "@angular/router/testing";
import { TranslocoTestingModule } from "@jsverse/transloco";
import { vi } from "vitest";

import en from "../../public/i18n/en.json";
import fr from "../../public/i18n/fr.json";
import { routes } from "./app.routes";
import type {
  Leaderboard,
  MatchCard,
  MatchView,
  PlayerSeason,
  Rules,
  Season,
  Summary,
} from "./results/api";
import type { Organization } from "./shared/organization";
import type { Me } from "./shared/session";

export const ALSACE: Organization = {
  accentColor: "#2F5BFF",
  defaultLocale: "fr",
  description: null,
  logo: null,
  name: "Alsace Arena",
  playersPerMatch: 10,
  slug: "alsace-arena",
  socialLinks: {
    discord: "https://discord.gg/alsace",
    instagram: null,
    tiktok: null,
    twitch: null,
    website: "https://alsace-arena.example",
    youtube: null,
  },
  tournamentName: "Spike Tournament",
};

export const ME: Me = {
  discordUsername: null,
  displayName: "Camille Martin",
  email: "camille@example.com",
  memberships: [
    {
      name: "Alsace Arena",
      owner: true,
      playerId: null,
      role: "ADMIN",
      slug: "alsace-arena",
    },
  ],
  platformAdmin: false,
  riotId: null,
};

const season = (number: number, finished: boolean): Season => ({
  endsOn: null,
  finished,
  matchCount: 8,
  maxGames: 5,
  minGames: 1,
  name: `Season ${number}`,
  number,
  startsOn: null,
});

/** Oldest first, as the API lists them. */
export const SEASONS: Season[] = [season(1, true), season(3, false)];

const BONUSES = [
  {
    award: "HIGHEST",
    code: "FB",
    label: "Most first bloods",
    points: 10,
    stat: "FIRST_BLOODS",
    threshold: null,
  },
  {
    award: "LOWEST",
    code: "LD",
    label: "Fewest deaths",
    points: 10,
    stat: "DEATHS",
    threshold: null,
  },
] satisfies Leaderboard["bonuses"];

export const LEADERBOARD: Leaderboard = {
  bonuses: BONUSES,
  columns: ["PERFORMANCE_SCORE", "KILLS", "DEATHS", "ASSISTS", "PLANTS"],
  season: { name: "Season 3", number: 3 },
  standings: [
    {
      averages: {
        ASSISTS: 4,
        DEATHS: 13,
        KILLS: 16.5,
        PERFORMANCE_SCORE: 220,
        PLANTS: 1.5,
      },
      bonuses: { FB: 2 },
      currentRank: { color: "#D6C07E", name: "Radiant" },
      games: 2,
      kda: 1.58,
      playerId: "kestrel",
      points: 5749,
      rank: 1,
      riotId: "Kestrel#EUW",
      totals: {
        ASSISTS: 8,
        DEATHS: 26,
        KILLS: 33,
        PERFORMANCE_SCORE: 440,
        PLANTS: 3,
      },
      wins: 1,
    },
    {
      averages: { DEATHS: 9, KILLS: 12, PERFORMANCE_SCORE: 180 },
      bonuses: { LD: 1 },
      currentRank: null,
      games: 1,
      kda: 2.22,
      playerId: "wren",
      points: 263,
      rank: 2,
      riotId: "Wren#EUW",
      totals: { DEATHS: 9, KILLS: 12, PERFORMANCE_SCORE: 180 },
      wins: 0,
    },
  ],
};

export const SUMMARY: Summary = {
  gamesPlayed: 2,
  matchCount: 8,
  matchesPlayed: 1,
  nextMatch: { matchDate: "2026-11-20", matchNumber: 2 },
  players: 2,
};

export const RULES: Rules = {
  bonuses: BONUSES,
  kdaPoints: 0,
  pointsPerWin: 50,
  stats: [
    { countable: true, points: 1, recorded: true, stat: "PERFORMANCE_SCORE" },
    { countable: true, points: 1, recorded: true, stat: "KILLS" },
    { countable: true, points: -1, recorded: true, stat: "DEATHS" },
    { countable: false, points: 0, recorded: true, stat: "ADR" },
    { countable: true, points: 1, recorded: false, stat: "CLUTCHES" },
  ],
};

/** Season 3's matches: one played, then two still to play (one without a date). */
export const MATCH_CARDS: MatchCard[] = [
  {
    games: 2,
    matchDate: "2026-11-13",
    matchNumber: 1,
    players: 2,
    topPlayer: { playerId: "kestrel", points: 5749, riotId: "Kestrel#EUW" },
  },
  {
    games: 0,
    matchDate: "2026-11-20",
    matchNumber: 2,
    players: 0,
    topPlayer: null,
  },
  { games: 0, matchDate: null, matchNumber: 3, players: 0, topPlayer: null },
];

/** Match 1 of season 3: Kestrel wins game 1, Wren game 2; Kestrel is the match's MVP. */
export const MATCH: MatchView = {
  bonuses: BONUSES,
  columns: LEADERBOARD.columns,
  combined: LEADERBOARD.standings,
  games: [
    {
      gameNumber: 1,
      lines: [
        {
          bonuses: ["FB"],
          playerId: "kestrel",
          points: 3200,
          riotId: "Kestrel#EUW",
          stats: { ASSISTS: 5, DEATHS: 12, KILLS: 20, PERFORMANCE_SCORE: 240 },
          win: true,
        },
      ],
    },
    {
      gameNumber: 2,
      lines: [
        {
          bonuses: ["LD"],
          playerId: "wren",
          points: 2900,
          riotId: "Wren#EUW",
          stats: { DEATHS: 9, KILLS: 12, PERFORMANCE_SCORE: 180 },
          win: true,
        },
        {
          bonuses: ["FB"],
          playerId: "kestrel",
          points: 2549,
          riotId: "Kestrel#EUW",
          stats: { ASSISTS: 3, DEATHS: 14, KILLS: 13, PERFORMANCE_SCORE: 200 },
          win: false,
        },
      ],
    },
  ],
  matchDate: "2026-11-13",
  matchNumber: 1,
  season: { name: "Season 3", number: 3 },
};

/** The app's routes, a fake back end, and both languages loaded from the real files. */
export const testProviders = (): (Provider | EnvironmentProviders)[] => [
  provideHttpClient(),
  provideHttpClientTesting(),
  provideRouter(routes, withComponentInputBinding()),
  importProvidersFrom(
    TranslocoTestingModule.forRoot({
      langs: { en, fr },
      preloadLangs: true,
      translocoConfig: {
        availableLangs: ["en", "fr"],
        defaultLang: "en",
        reRenderOnLangChange: true,
      },
    })
  ),
];

/** An element's text with its whitespace collapsed. */
export const text = (element: Element | null | undefined): string | undefined =>
  element?.textContent?.replaceAll(/\s+/gu, " ").trim();

export const texts = (
  root: ParentNode,
  selector: string
): (string | undefined)[] =>
  [...root.querySelectorAll(selector)].map((element) => text(element));

/** The page a harness shows; fails the test when the route rendered nothing. */
export const rendered = (harness: RouterTestingHarness): HTMLElement => {
  const page = harness.routeNativeElement;

  if (!page) {
    throw new Error("The route rendered nothing");
  }

  return page;
};

/** What the fake back end answers. */
type Answer =
  | Organization
  | Season[]
  | Leaderboard
  | Summary
  | Rules
  | Me
  | MatchCard[]
  | MatchView
  | PlayerSeason;

/** Answers a request once the app makes it, running change detection while it waits. */
export const answer = async (
  harness: RouterTestingHarness,
  url: string,
  body: Answer
): Promise<void> => {
  const http = TestBed.inject(HttpTestingController);

  await vi.waitFor(() => {
    harness.detectChanges();
    http.expectOne(url).flush(body);
  });
};

/**
 * Opens an address of Alsace Arena, answering `GET /api/orgs/alsace-arena` and its seasons
 * (none by default, so the leaderboard asks for nothing more).
 */
export const openAlsace = async (
  url: string,
  seasons: Season[] = []
): Promise<RouterTestingHarness> => {
  const harness = await RouterTestingHarness.create();
  const navigation = harness.navigateByUrl(url);

  await answer(harness, `/api/orgs/${ALSACE.slug}`, ALSACE);
  await navigation;
  await answer(harness, `/api/orgs/${ALSACE.slug}/seasons`, seasons);
  harness.detectChanges();

  return harness;
};

/**
 * Opens an address as `me` (nobody when null), for pages behind the admin guard, which asks who is
 * signed in first; then answers the organisation and its seasons.
 */
export const openAs = async (
  url: string,
  me: Me | null,
  seasons: Season[] = []
): Promise<RouterTestingHarness> => {
  const http = TestBed.inject(HttpTestingController);
  const harness = await RouterTestingHarness.create();
  const navigation = harness.navigateByUrl(url);

  await vi.waitFor(() => {
    harness.detectChanges();
    const request = http.expectOne("/api/me");

    if (me) {
      request.flush(me);
    } else {
      request.flush(null, { status: 401, statusText: "Unauthorized" });
    }
  });
  await answer(harness, `/api/orgs/${ALSACE.slug}`, ALSACE);
  await navigation;
  await answer(harness, `/api/orgs/${ALSACE.slug}/seasons`, seasons);
  harness.detectChanges();

  return harness;
};
