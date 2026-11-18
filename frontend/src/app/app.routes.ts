import type { Routes } from "@angular/router";

import { adminGuard } from "./admin/admin-guard";
import { PlayersAdminPage } from "./admin/players/players";
import { OrgShell } from "./layout/org-shell";
import { PlatformShell } from "./layout/platform-shell";
import { LEGAL_PAGES } from "./legal/legal-content";
import { LegalPage } from "./legal/legal-page";
import { NotFoundPage } from "./not-found/not-found";
import { LeaderboardPage } from "./results/leaderboard/leaderboard";
import { MatchPage } from "./results/match/match";
import { MatchesPage } from "./results/matches/matches";
import { organizationResolver } from "./shared/organization";
import { SignInPage } from "./sign-in/sign-in";

export const routes: Routes = [
  // Release 1 has one organisation; the home page with the featured one comes in release 2.
  { path: "", pathMatch: "full", redirectTo: "o/alsace-arena" },
  {
    children: [
      { component: LeaderboardPage, path: "" },
      { component: MatchesPage, path: "matches" },
      { component: MatchPage, path: "matches/:match" },
      {
        canActivate: [adminGuard],
        children: [
          { path: "", pathMatch: "full", redirectTo: "players" },
          { component: PlayersAdminPage, path: "players" },
        ],
        path: "admin",
      },
    ],
    component: OrgShell,
    path: "o/:slug",
    resolve: { organization: organizationResolver },
    runGuardsAndResolvers: "pathParamsChange",
  },
  {
    children: [
      { component: SignInPage, path: "sign-in" },
      ...LEGAL_PAGES.map((page) => ({
        component: LegalPage,
        data: { page },
        path: page,
      })),
      { component: NotFoundPage, path: "not-found" },
      { component: NotFoundPage, path: "**" },
    ],
    component: PlatformShell,
    path: "",
  },
];
