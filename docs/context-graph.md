# Context graph

A map of the rewrite: what each part is, what it depends on, and what's built so far. Keep it
current: any pull request that adds, removes or reconnects a package, endpoint, table, screen,
workflow or phase updates this file in the same pull request.

Last updated: 2026-11-18, with the Matches page and a match's page.

## System

```mermaid
flowchart LR
    browser[Browser<br/>Angular SPA] -->|HTTPS| cf[Cloudflare]
    cf --> caddy[Shared Caddy<br/>irmahs-labs/proxy]
    caddy -->|proxy network :8080| app[Spring Boot jar<br/>API + Angular files]
    app -->|JDBC as leaderboard_app| db[(Postgres 17<br/>internal network)]
    app -->|OAuth 2 / OIDC| google[Google sign-in]
    flyway[Flyway, as leaderboard_owner] --> db
    legacy[legacy/ Next.js on Vercel<br/>frozen, live until cutover] -.-> supabase[(Supabase)]
    supabase -.->|deploy/import, once at cutover| db
```

## Repository

```mermaid
flowchart TD
    root[pom.xml<br/>parent, Spring Boot 4.1, Java 21] --> fe[frontend/<br/>Angular 22]
    root --> be[backend/<br/>Spring Boot]
    fe -->|jar with static/| be
    be --> mig[db/migration<br/>Flyway scripts]
    deploy[deploy/<br/>db-init, import, README] --> prod[compose.prod.yaml]
    ci[.github/workflows/ci.yml<br/>mvnw verify + SonarQube Cloud] --> root
    dep[.github/workflows/deploy.yml<br/>on push to migration] --> prod
    legacy[legacy/<br/>frozen: never touch]
```

## Backend packages

`dev.irmahs.leaderboard.*`. An arrow means "imports from".

```mermaid
flowchart LR
    security --> accounts
    accounts --> members
    accounts --> organizations
    tenancy --> organizations
    players --> organizations
    players --> ranks
    seasons --> organizations
    matches --> seasons
    scoring --> seasons
    scoring --> stats
    stats --> matches
    stats --> organizations
    stats --> players
    stats --> ranks
    stats --> scoring
    stats --> seasons
    system
```

| Package | Holds |
|---|---|
| `accounts` | `AppUser`, `GoogleProfile`, `AccountService` (sign-in, initial memberships, platform admins), `AccessProperties`, `MeController` |
| `members` | `OrganizationMember` (role ADMIN or PLAYER, one owner), `Membership` |
| `organizations` | `Organization`, `OrganizationService` (`requireActive`, `recordActivity`), public org endpoint |
| `security` | `SecurityConfig` (CSRF for SPAs, 401 on `/api/**`, Google sign-in ending on the Angular `/sign-in`), `GoogleUserService` (verified email only), `OrgAccess` (`@orgAccess.isAdmin`) |
| `tenancy` | `OrganizationScopeFilter` + `OrganizationScopedTransactionManager`: sets `app.organization_id` per transaction for row-level security |
| `players` | `Player`, `PlayerAlias`, `AliasKind`, `PlayerAdminService` (search, add, edit; rename keeps the old Riot ID as an alias) |
| `ranks` | `Rank`, public rank list |
| `seasons` | `Season` (rules, bonuses, shape), `Stat` (`isCountable`), `StatRule`, `SeasonBonus`, `PlatformDefaults`, `SeasonService` (public), `SeasonAdminService` |
| `matches` | `Match`, `Game` and their repositories |
| `scoring` | `ScoringService` (the only place totals are computed), `BonusCalculator` (HIGHEST/LOWEST, ties all win) |
| `stats` | `GameStat`, `StatLine`, `PlayerTally`, `SeasonResultsService` (public results; each standing carries the player's current rank), `ScoresService` (Enter scores) |
| `system` | `/api/version`; `SpaController` forwards the Angular router's addresses (`/o/**`, `/not-found`, `/sign-in`, `/legal`, `/privacy`, `/cookies`, `/terms`) to `index.html` |

## API

Admin routes need `@orgAccess.isAdmin(#slug, authentication)` and a CSRF token on changes.

```mermaid
flowchart LR
    subgraph public[Public GET]
        v["/api/version"]
        r["/api/ranks"]
        o["/api/orgs/{slug}"]
        sl["…/seasons"]
        sr["…/seasons/{n}/rules"]
        lb["…/seasons/{n}/leaderboard, /summary"]
        ml["…/seasons/{n}/matches, /matches/{m}"]
    end
    subgraph signedin[Signed in]
        me["/api/me"]
    end
    subgraph admin["Admin: /api/orgs/{slug}/admin"]
        ap["players: GET ?q, POST, PUT /{id}"]
        as["seasons: POST, PUT /{n}, PUT /{n}/recorded-stats"]
        sc["seasons/{n}/matches/{m}/games/{g}: GET, PUT, DELETE, /previous-roster"]
        bc["seasons/{n}/bonus-check: POST"]
    end
    r --> ranks
    o --> organizations
    sl & sr --> SeasonService
    lb & ml --> SeasonResultsService
    me --> AccountService
    ap --> PlayerAdminService
    as --> SeasonAdminService
    sc & bc --> ScoresService
```

## Data model

Flyway: `V2026_11_10_1__schema.sql` (all tables), `V2026_11_13_1__row_level_security.sql`
(policy `organization_isolation` on players, seasons, matches, organization_members,
organization_invites and organization_logos), `V2026_11_18_1__drop_avatar_url.sql` (the Google
picture isn't kept: data minimisation).

```mermaid
erDiagram
    organizations ||--o{ organization_members : has
    app_users ||--o{ organization_members : is
    organizations ||--o{ organization_invites : sends
    organizations ||--o| organization_logos : shows
    organizations ||--o{ players : has
    ranks ||--o{ players : "current rank"
    players ||--o{ player_aliases : "also known as"
    players |o--o| organization_members : "linked to"
    organizations ||--o{ seasons : runs
    seasons ||--o{ season_stat_rules : records
    seasons ||--o{ season_bonuses : awards
    seasons ||--o{ matches : has
    matches ||--o{ games : has
    games ||--o{ game_stats : "one line per player"
    players ||--o{ game_stats : plays
    game_stats ||--o{ game_stat_bonuses : wins
    season_bonuses ||--o{ game_stat_bonuses : "won as"
```

No entity yet: `organization_invites`, `organization_logos`. Mapped as collections rather than
entities: `season_stat_rules` (a map on `Season`) and `game_stat_bonuses` (a many-to-many on
`GameStat`).

## Frontend

`frontend/src/app`, organised by feature like the backend: `shared/` for what every feature uses,
`layout/` for the two shells, then one folder per feature. Solid arrows exist; dashed ones are
planned.

```mermaid
flowchart TD
    app[App<br/>starts ThemeService, LanguageService, SessionService] --> routes[app.routes.ts]
    routes -->|"/ redirects to /o/alsace-arena"| shell
    routes -->|"/o/:slug, organizationResolver"| shell[OrgShell<br/>accent, language, title, season]
    routes -->|"/sign-in, /legal, /privacy, /cookies, /terms, /not-found, **"| platform[PlatformShell<br/>platform name and red]
    platform --> si[SignInPage<br/>Google, your organisations, sign out]
    platform --> legal[LegalPage<br/>legal notice, privacy, cookies, terms]
    platform --> nf[NotFoundPage]
    shell --> header[SiteHeader<br/>skip link, logo, nav keeping ?season=, season, EN/FR, theme, Sign in or account]
    shell --> footer[SiteFooter<br/>org name, credit, legal pages, GitHub, contact]
    platform --> header
    platform --> footer
    shell -->|"organisation, ?season="| current[CurrentSeason]
    current -->|GET| seasonsApi["…/seasons"]
    header -->|season picker| current
    si -->|"leaves for"| google["/oauth2/authorization/google"]
    google -->|"back to /sign-in, then the page sign-in began on"| si
    header --> session[SessionService] -->|GET /api/me, POST /api/logout| meApi["/api/me"]
    shell --> lb["LeaderboardPage<br/>top three, tiles, how points are counted"]
    lb --> table[StandingsTable]
    lb --> current
    lb -->|GET| lbApi["…/seasons/{n}/leaderboard, /summary, /rules"]
    table --> pill[RankPill]
    lb --> pill
    shell -->|"/o/:slug/matches"| mt["MatchesPage<br/>a card per match, its MVP"]
    mt --> current
    mt -->|GET| mtApi["…/seasons/{n}/matches"]
    shell -->|"/o/:slug/matches/:match?season="| md["MatchPage<br/>MVP, stat leaders, games, overall or one game"]
    md --> current
    md --> table
    md -->|GET| mdApi["…/seasons/{n}/matches/{m}"]
    shell -.->|Phase 4| adm[Enter scores, Players, Seasons, Record switches]
    adm -.-> pill
    resolver[organizationResolver] -->|GET| orgApi["/api/orgs/{slug}"]
    shell --- resolver
```

| Folder | Holds |
|---|---|
| `shared/` | `ThemeService` (dark or light, the accent's CSS variables), `colour.ts` (WCAG contrast and mixing, the palettes, readable rank colours), `LanguageService` (EN or FR; numbers, dates and plurals in the language on screen), `SessionService` (who is signed in, Google sign-in with a safe return address, sign-out), `organization.ts` (`organizationResolver`, `fullName`, `initials`), `storage.ts` (local and session storage that survives being blocked), `RankPill` |
| `layout/` | `OrgShell` (an organisation's pages), `PlatformShell` (sign-in and Not found), `SiteHeader`, `SiteFooter`, page layout |
| `results/` | The public results: `api.ts` (the API's shapes), `CurrentSeason` (the organisation's seasons, newest first, and the one picked with `?season=`), `stats.ts` (stat columns with K, D and A folded into KDA, decimals, the points formula, Riot ID name and tag), `StandingsTable` (a season's or a match's totals, or one game's lines), `leaderboard/`, `matches/` (`MatchesPage`), `match/` (`MatchPage`, `match-stats.ts`: a game's lines as standings, the stat leaders) |
| `sign-in/` | `SignInPage` |
| `legal/` | `LegalPage` and `legal-content.ts`: the legal notice, privacy policy, cookie policy and terms, in English and French, one route each |
| `not-found/` | `NotFoundPage` |
| `testing.ts` | Fixtures and helpers shared by the tests (`openAlsace`, `answer`, `text`) |
| `styles.css` | The template's tokens for both themes, Barlow fonts (bundled from `@fontsource`), shared classes (`.panel`, `.pill`, `.heading`, `.lede`, `.label`, buttons) |
| `public/i18n/` | `en.json`, `fr.json` |
| `public/` | `robots.txt`, and `llms.txt` describing the public API for AI agents |

A component used by several features lives in `shared/` (`RankPill`); one used by several pages of a
single feature lives in that feature (`StandingsTable` in `results/`).

Not in the shell yet: the online count (no backend), social links (branding API, phase 7). Not on the
leaderboard yet: Export CSV and Export poster (phase 11).

## Phases

```mermaid
flowchart LR
    p0[0 Setup ✓] --> p1[1 Schema, import, public API ✓] --> p2[2 Sign-in and admin API ✓]
    p2 --> p3[3 Angular shell and public pages ▶] --> p4[4 Angular admin] --> p5[5 Deploy pipelines] --> p6[6 Cutover]
    p6 --> p7[7 Open the platform<br/>release 2]
    p7 --> p8[8 Scoring rules editor] --> p9[9 Players and claims] --> p10[10 Seasons, matches<br/>and waiting lists]
    p10 --> p11[11 Exports and poster] --> p12[12 Moderation] --> p13[13 Merge tool and scan]
```

✓ done · ▶ next. The full plan, with each phase's exit criterion, is the migration plan page
kept with the project designs.

Not built yet: the online count (no phase yet; needs a backend before the header shows it);
social links, branding, members and invites (7); the scoring-rules editor,
`PUT …/rules?apply=current|next` (8); claims and expected emails (9); schedule and sign-ups (10);
exports (11); moderation (12); merging duplicate players (13).
