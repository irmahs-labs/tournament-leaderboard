import type { Routes } from "@angular/router";

import { organizationResolver } from "./core/organization";
import { OrgShell } from "./layout/org-shell";
import { LeaderboardPage } from "./pages/leaderboard";
import { MatchesPage } from "./pages/matches";
import { NotFoundPage } from "./pages/not-found";

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
  { component: NotFoundPage, path: "not-found" },
  { component: NotFoundPage, path: "**" },
];
