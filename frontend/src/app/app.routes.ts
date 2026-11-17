import type { Routes } from "@angular/router";

import { OrgShell } from "./layout/org-shell";
import { PlatformShell } from "./layout/platform-shell";
import { NotFoundPage } from "./not-found/not-found";
import { LeaderboardPage } from "./results/leaderboard/leaderboard";
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
    ],
    component: OrgShell,
    path: "o/:slug",
    resolve: { organization: organizationResolver },
    runGuardsAndResolvers: "pathParamsChange",
  },
  {
    children: [
      { component: SignInPage, path: "sign-in" },
      { component: NotFoundPage, path: "not-found" },
      { component: NotFoundPage, path: "**" },
    ],
    component: PlatformShell,
    path: "",
  },
];
