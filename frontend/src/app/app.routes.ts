import type { Routes } from "@angular/router";

import { AccountPage } from "./account/account";
import { accountGuard } from "./account/account-guard";
import { adminGuard } from "./admin/admin-guard";
import { BrandingAdminPage } from "./admin/branding/branding";
import { ClaimsAdminPage } from "./admin/claims/claims";
import { MembersAdminPage } from "./admin/members/members";
import { PlayersAdminPage } from "./admin/players/players";
import { RosterAdminPage } from "./admin/roster/roster";
import { ScheduleAdminPage } from "./admin/schedule/schedule";
import { ScoresAdminPage } from "./admin/scores/scores";
import { ScoringAdminPage } from "./admin/scoring/scoring";
import { SeasonsAdminPage } from "./admin/seasons/seasons";
import { SignUpsAdminPage } from "./admin/sign-ups/sign-ups";
import { ClaimPage } from "./claim/claim";
import { HomePage } from "./home/home";
import { JoinPage } from "./join/join";
import { OrgShell } from "./layout/org-shell";
import { PlatformShell } from "./layout/platform-shell";
import { LEGAL_PAGES } from "./legal/legal-content";
import { LegalPage } from "./legal/legal-page";
import { NotFoundPage } from "./not-found/not-found";
import { PlatformPage } from "./platform/platform";
import { platformGuard } from "./platform/platform-guard";
import { LeaderboardPage } from "./results/leaderboard/leaderboard";
import { MatchPage } from "./results/match/match";
import { MatchesPage } from "./results/matches/matches";
import { PlayerPage } from "./results/player/player";
import { PlayersPage } from "./results/players/players";
import { organizationResolver } from "./shared/organization";
import { SignInPage } from "./sign-in/sign-in";
import { MySeasonsPage } from "./sign-ups/my-seasons";

export const routes: Routes = [
  {
    children: [
      { component: LeaderboardPage, path: "" },
      { component: MatchesPage, path: "matches" },
      { component: MatchPage, path: "matches/:match" },
      { component: PlayersPage, path: "players" },
      { component: PlayerPage, path: "players/:player" },
      { canActivate: [accountGuard], component: ClaimPage, path: "claim" },
      {
        canActivate: [accountGuard],
        component: MySeasonsPage,
        path: "me/seasons",
      },
      {
        canActivate: [adminGuard],
        children: [
          { path: "", pathMatch: "full", redirectTo: "scores" },
          { component: ScoresAdminPage, path: "scores" },
          { component: PlayersAdminPage, path: "players" },
          { component: SeasonsAdminPage, path: "seasons" },
          {
            component: SignUpsAdminPage,
            path: "seasons/:season/sign-ups",
          },
          { component: ScheduleAdminPage, path: "schedule" },
          { component: RosterAdminPage, path: "matches/:match/roster" },
          { component: ScoringAdminPage, path: "scoring" },
          { component: MembersAdminPage, path: "members" },
          { component: BrandingAdminPage, path: "branding" },
          { component: ClaimsAdminPage, path: "claims" },
        ],
        path: "admin",
      },
    ],
    component: OrgShell,
    path: "org/:slug",
    resolve: { organization: organizationResolver },
    runGuardsAndResolvers: "pathParamsChange",
  },
  {
    children: [
      { component: HomePage, path: "", pathMatch: "full" },
      { component: SignInPage, path: "sign-in" },
      {
        canActivate: [platformGuard],
        component: PlatformPage,
        path: "platform",
      },
      { canActivate: [accountGuard], component: AccountPage, path: "account" },
      { component: JoinPage, path: "join" },
      { component: JoinPage, path: "join/:code" },
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
