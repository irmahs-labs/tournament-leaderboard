# Context graph

A map of the rewrite: what each part is, what it depends on, and what's built so far. Keep it
current: any pull request that adds, removes or reconnects a package, endpoint, table, screen,
workflow or phase updates this file in the same pull request.

Last updated: 2026-11-22, with every Riot ID shown with its #tag in a lighter grey.

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
    supabase -.->|deploy/import, on-server.sh at cutover| db
```

## Repository

```mermaid
flowchart TD
    root[pom.xml<br/>parent, Spring Boot 4.1, Java 21] --> fe[frontend/<br/>Angular 22]
    root --> be[backend/<br/>Spring Boot]
    fe -->|jar with static/| be
    be --> mig[db/migration<br/>Flyway scripts]
    deploy[deploy/<br/>db-init, import, rollout.sh, README] --> prod[compose.prod.yaml]
    ci[.github/workflows/ci.yml<br/>mvnw verify + SonarQube Cloud] --> root
    ci -->|"after build, on a push to migration"| dep[.github/workflows/deploy.yml<br/>dump, rollout.sh: the new app beside the old, then the old stopped; health check through Caddy]
    dep --> prod
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
    team --> accounts
    team --> members
    team --> organizations
    founding --> accounts
    founding --> members
    founding --> organizations
    platform --> accounts
    platform --> members
    platform --> organizations
    claims --> accounts
    claims --> members
    claims --> organizations
    claims --> players
    signups --> accounts
    signups --> members
    signups --> organizations
    signups --> players
    signups --> seasons
    rosters --> accounts
    rosters --> matches
    rosters --> members
    rosters --> organizations
    rosters --> players
    rosters --> seasons
    rosters --> signups
    presence
    system
```

| Package | Holds |
|---|---|
| `accounts` | `AppUser`, `GoogleProfile`, `AccountService` (sign-in, initial memberships, platform admins, linking a player whose expected email matches, deleting an account unless it owns an organisation), `AccessProperties`, `IdentityService` (the account's Riot ID and Discord: changed on every linked profile at once, given to a profile when it's linked, taken from the first one), `MeController` (`GET` and `DELETE /api/me`, signing out after a deletion; `PUT /api/me/identity`; `/api/me` lists active memberships and, apart, suspended ones) |
| `members` | `OrganizationMember` (role ADMIN or PLAYER, one owner), `Membership` |
| `organizations` | `Organization` (with its `SocialLinks`), `OrganizationService` (`requireActive`, `recordActivity`), `OrganizationView`, `BrandingService` (names, language, accent, places per match, links checked by `SocialSite`; the logo, told apart by its bytes), `OrganizationLogo`, the public org and logo endpoints, `BrandingController` |
| `security` | `SecurityConfig` (CSRF for SPAs, 401 on `/api/**`, Google sign-in ending on the Angular `/sign-in`), `GoogleUserService` (verified email only), `OrgAccess` (`@orgAccess.isAdmin`; `/api/orgs/*/me/**` needs sign-in, even to read), `SessionConfig` (sessions in the database through Spring Session JDBC, so deploys don't sign anyone out; an attribute a new version can't read signs that person out instead of failing) |
| `tenancy` | `OrganizationScopeFilter` + `OrganizationScopedTransactionManager`: sets `app.organization_id` per transaction for row-level security |
| `players` | `Player`, `PlayerAlias`, `AliasKind`, `PlayerAdminService` (search, add, edit, a Google email expected in advance seen by admins only; `rerank` for a linked player; a linked player's Riot ID and Discord are refused to admins (409) and the list marks them `linked`; rename keeps the old Riot ID as an alias), `PlayerController` (the public list: Riot ID and aliases; Discord only for admins and members claiming) |
| `ranks` | `Rank`, public rank list |
| `seasons` | `Season` (rules, bonuses, shape, KDA points), `Stat` (`isCountable`; `PLACE` and `HONOURS` among them), `StatRule` (recorded, points per unit), `SeasonBonus`, `BonusAward` (HIGHEST, LOWEST, MANUAL, TOP_WINNER, TOP_LOSER, AT_LEAST), `PlatformDefaults`, `Points` (points as the API writes them), `RulesSnapshot` (a season's rules on their own), `NextSeasonRules` (rules kept for the next season, as JSON) and `NextSeasonRulesController`, `SeasonService` (public), `SeasonAdminService` |
| `matches` | `Match` (planned day, time, place, reply-by date, places, lock, called off), `Game` and their repositories; `lockById` locks a match's row for every roster change |
| `scoring` | `ScoringService` (the only place points are computed: per game, a match or season adding up its games; a stat not recorded doesn't count), `BonusCalculator` (HIGHEST/LOWEST, MVP/SVP from the place, AT_LEAST; ties all win; nothing from a stat not recorded) |
| `stats` | `GameStat`, `StatLine`, `PlayerTally`, `SeasonResultsService` (public results; each standing carries the player's current rank), `ScoresService` (Enter scores), `RulesAdminService` (the Scoring screen: changes a season's rules and works out every saved game's automatic bonuses again; adds the admins' own bonuses, coded from their name, and removes bonuses with their awards; a finished season's rules can't change) |
| `founding` | `FoundingService` (anyone signed in creates an organisation and owns it: address checked and not reserved, 3 owned at most except for platform admins, 409 when taken), `FoundingController` |
| `platform` | `PlatformService` (platform admins only: every organisation with its owner, members, activity and whether the home page features it; suspending and reactivating), `PlatformController` |
| `claims` | `PlayerClaim` (player_claims), `ClaimService` (join as a player; claim an unclaimed player with a note; cancel; creating one's own profile with no admin (the account's Riot ID and Discord when it has them; refused while a claim waits or the Riot ID is taken); the admins' queue; accepting links the member and declines the other claims for that player; nobody accepts their own while another admin exists, the only admin's is marked self-approved; the linked player edits their own details), `ClaimController` |
| `signups` | `SeasonRegistration` (season_registrations: REGISTERED or WITHDRAWN), `SignUpService` (a linked player signs up while `Season.takesSignUps`, and withdraws; admins open or close sign-ups, sign anyone up at any time before the season is over, players without an account too, and withdraw people), `SignUpController` |
| `rosters` | `Attendance` (match_attendance: CONFIRMED, WAITING or DECLINED, from the app, an admin or the waiting list), `RosterService` (the season's schedule; planning, calling off; a match's roster with who hasn't answered; an admin moving anyone, even without an account or once locked, never past the places; places never below those confirmed; locking; a signed-up player's upcoming matches and their answer: a place while there is one, the waiting list after that, first come first served), `RosterController` |
| `team` | `Invite` (organization_invites; only a hash of the code), `InviteCodes` (8 characters without look-alikes, SHA-256), `TeamService` (the Members screen: roles, removal, ownership, admin invites), `JoinService` (what a code is for; using it, its row locked), `MembershipController` (leaving an organisation, never as its owner), `CodeLookupLimit` (10 lookups a minute per address, in memory) |
| `presence` | `OnlineCount` (open pages, in memory only; changes sent at most once a second, a heartbeat every 25 s), `GET /api/online` as server-sent events |
| `system` | `/api/version`; `UnknownPageResolver` (a browser on an address nobody serves gets the app's Not found page with a 404; the API and files keep JSON problems); `SpaController` forwards the Angular router's addresses (`/org/**` (the claim page included), `/not-found`, `/platform`, `/account`, `/join`, `/join/*`, `/sign-in`, `/legal`, `/privacy`, `/cookies`, `/terms`) to `index.html` |

## API

Admin routes need `@orgAccess.isAdmin(#slug, authentication)` and a CSRF token on changes.

```mermaid
flowchart LR
    subgraph public[Public GET]
        v["/api/version"]
        on["/api/online (event stream)"]
        r["/api/ranks"]
        fe["/api/featured: the most recently active organisation, 204 before any game"]
        o["/api/orgs/{slug}"]
        lo["/api/orgs/{slug}/logo: kept a year, its address changes with it"]
        sl["…/seasons"]
        sr["…/seasons/{n}/rules"]
        lb["…/seasons/{n}/leaderboard, /summary"]
        ml["…/seasons/{n}/matches, /matches/{m}"]
        ps["…/seasons/{n}/players/{id}: a player's season match by match"]
        pl2["/api/orgs/{slug}/players"]
    end
    subgraph signedin[Signed in]
        me["/api/me: GET, DELETE (delete my account); /api/me/identity: PUT (Riot ID and Discord everywhere)"]
        lv["/api/orgs/{slug}/membership: DELETE (leave)"]
        acc["/api/invites/{code}/accept: POST"]
        fo["/api/orgs: POST, create an organisation"]
        cl["/api/orgs/{slug}/membership: POST (join); /claim: GET, POST, DELETE; /claim/players: GET; /claim/player: PUT (my rank here); /players/me: POST (create my profile)"]
        su["/api/orgs/{slug}/me/seasons: GET; /seasons/{n}/registration: POST, DELETE"]
        mm["/api/orgs/{slug}/me/matches: GET; /matches/{id}/attendance: PUT"]
        pl["/api/platform/organizations: GET; /{slug}/status: PUT (platform admins)"]
    end
    jv["/api/invites/{code}: GET, public, 10 a minute"]
    subgraph admin["Admin: /api/orgs/{slug}/admin"]
        ap["players: GET ?q, POST, PUT /{id}"]
        br["branding: PUT; logo: PUT (the image as the body), DELETE"]
        cq["claims: GET ?status=, POST /{id}/accept, POST /{id}/decline"]
        tm["members: GET, PUT /{user}/role, DELETE /{user}, POST /{user}/ownership; invites: POST, DELETE /{id}"]
        as["seasons: POST, PUT /{n}"]
        ru["seasons/{n}/rules: PUT ?apply=current|next, POST /platform-defaults, POST /bonuses, DELETE /bonuses/{code}"]
        nx["next-season-rules: GET, DELETE"]
        sc["seasons/{n}/matches/{m}/games/{g}: GET, PUT, DELETE, /previous-roster"]
        bc["seasons/{n}/bonus-check: POST"]
        asu["seasons/{n}/signup: PUT; seasons/{n}/registrations: GET, POST /{player}, DELETE /{player}"]
        sch["seasons/{n}/schedule: GET; /schedule/{m}: PUT; matches/{id}/roster: GET, PUT and DELETE /{player}; /places: PUT; /lock: POST, DELETE"]
    end
    r --> ranks
    on --> presence
    o & lo & br --> BrandingService
    sl & sr --> SeasonService
    lb & ml & ps --> SeasonResultsService
    pl2 --> players
    me --> AccountService
    lv --> TeamService
    fe --> BrandingService
    fo --> FoundingService
    cl & cq --> ClaimService
    su & asu --> SignUpService
    mm & sch --> RosterService
    pl --> PlatformService
    tm --> TeamService
    jv & acc --> JoinService
    ap --> PlayerAdminService
    as --> SeasonAdminService
    sc & bc --> ScoresService
    ru --> RulesAdminService
    nx --> NextSeasonRules
```

## Data model

Flyway: `V2026_11_10_1__schema.sql` (all tables), `V2026_11_13_1__row_level_security.sql`
(policy `organization_isolation` on players, seasons, matches, organization_members,
organization_invites and organization_logos), `V2026_11_18_1__drop_avatar_url.sql` (the Google
picture isn't kept: data minimisation), `V2026_11_19_1__weighted_scoring.sql`,
`V2026_11_19_2__social_links.sql` (six `*_url` columns on organizations, https only),
`V2026_11_20_1__player_claims.sql` (player_claims, one waiting per member, with the same policy),
`V2026_11_20_2__expected_email.sql` (players.expected_email, once per organisation), `V2026_11_20_3__account_identity.sql` (app_users.riot_id and discord_username, filled from each linked account's first profile), `V2026_11_20_4__rank_colours.sql` (a colour per Valorant tier, for ranks that had none), `V2026_11_20_5__jpeg_logos.sql` (organization_logos also takes image/jpeg), `V2026_11_20_6__sessions.sql` (spring_session and spring_session_attributes, Spring Session's own schema), `V2026_11_20_7__season_sign_ups.sql` (seasons.signup_open; season_registrations, with the same policy), `V2026_11_22_1__match_schedule.sql` (matches' start time, place, reply-by date, places, lock and status; match_attendance, with the same policy).

```mermaid
erDiagram
    organizations ||--o{ organization_members : has
    app_users ||--o{ organization_members : is
    organizations ||--o{ organization_invites : sends
    organizations ||--o| organization_logos : shows
    spring_session ||--o{ spring_session_attributes : holds
    organizations ||--o{ players : has
    ranks ||--o{ players : "current rank"
    players ||--o{ player_aliases : "also known as"
    players |o--o| organization_members : "linked to"
    players ||--o{ player_claims : "claimed in"
    app_users ||--o{ player_claims : sends
    organizations ||--o{ seasons : runs
    seasons ||--o{ season_stat_rules : records
    seasons ||--o{ season_bonuses : awards
    seasons ||--o{ matches : has
    seasons ||--o{ season_registrations : "signed up"
    matches ||--o{ match_attendance : "answered for"
    players ||--o{ match_attendance : answers
    players ||--o{ season_registrations : "signs up for"
    matches ||--o{ games : has
    games ||--o{ game_stats : "one line per player"
    players ||--o{ game_stats : plays
    game_stats ||--o{ game_stat_bonuses : wins
    season_bonuses ||--o{ game_stat_bonuses : "won as"
```

Mapped as collections rather than
entities: `season_stat_rules` (a map on `Season`) and `game_stat_bonuses` (a many-to-many on
`GameStat`).

## Frontend

`frontend/src/app`, organised by feature like the backend: `shared/` for what every feature uses,
`layout/` for the two shells, then one folder per feature. Solid arrows exist; dashed ones are
planned.

```mermaid
flowchart TD
    app[App<br/>starts ThemeService, LanguageService, SessionService] --> routes[app.routes.ts]
    routes -->|"/org/:slug, organizationResolver"| shell[OrgShell<br/>accent, language, title, season]
    routes -->|"/, /account, /platform, /sign-in, /join, /join/:code, /legal, /privacy, /cookies, /terms, /not-found, **"| platform[PlatformShell<br/>platform name and red]
    platform --> home[HomePage /<br/>what it does, create an organisation, the featured one]
    home -->|"GET /api/featured, POST /api/orgs"| foundApi["/api/featured, /api/orgs"]
    platform --> padmin[PlatformPage /platform, platformGuard<br/>counts, search, every organisation, suspend or reactivate]
    padmin -->|"GET, PUT …/status"| platformApi["/api/platform/organizations"]
    platform --> acct[AccountPage /account, accountGuard<br/>profile, Riot ID and Discord for every organisation, sign out, organisations and leaving, deleting the account]
    acct -->|"PUT /api/me/identity, DELETE /api/me, DELETE …/membership"| accountApi["/api/me, /api/orgs/{slug}/membership"]
    platform --> si[SignInPage<br/>Google, your organisations, sign out, create one]
    platform --> legal[LegalPage<br/>legal notice, privacy, cookies, terms]
    platform --> nf[NotFoundPage<br/>or, for its members, 'this organisation is suspended']
    platform --> join[JoinPage /join/:code<br/>what the code is for, sign in with the invited account, accept; or type a code]
    shell --> header[SiteHeader<br/>skip link, logo, nav keeping ?season= (My seasons for members; Scores, Players, Seasons, Schedule, Scoring, Members and Branding on admin pages; a Site | Admin switch for admins), online count, season, one EN / FR button, a light/dark pill switch, Sign in or My account]
    shell --> footer[SiteFooter<br/>org name and its social links; credit, legal pages, GitHub and Contact on three centred lines]
    platform --> header
    platform --> footer
    shell -->|"organisation, ?season="| current[CurrentSeason]
    current -->|GET| seasonsApi["…/seasons"]
    header -->|season picker| current
    si -->|"leaves for"| google["/oauth2/authorization/google"]
    google -->|"back to /sign-in, then the page sign-in began on"| si
    header -->|event stream| online[OnlineService] -->|GET| onlineApi["/api/online"]
    header --> session[SessionService] -->|GET /api/me, POST /api/logout| meApi["/api/me"]
    shell --> lb["LeaderboardPage<br/>top three, tiles, how points are counted"]
    lb --> table[StandingsTable]
    lb --> current
    lb -->|GET| lbApi["…/seasons/{n}/leaderboard, /summary, /rules"]
    table --> pill[RankPill]
    lb --> pill
    shell -->|"/org/:slug/players"| plp["PlayersPage<br/>a card per player, search, sort, rank filter"]
    shell -->|"/org/:slug/claim, accountGuard"| clp["ClaimPage<br/>join, pick your profile, note, wait or cancel; or create one"]
    shell -->|"/org/:slug/me/seasons, accountGuard"| mys["MySeasonsPage<br/>the seasons under way, sign up, withdraw; each upcoming match: I'm in, Can't make it"]
    mys -->|"GET, POST, DELETE"| suApi["…/me/seasons, …/seasons/{n}/registration"]
    mys -->|"GET, PUT"| mmApi["…/me/matches, …/matches/{id}/attendance"]
    shell -->|"/org/:slug/players/:player?season="| pp["PlayerPage<br/>standing, the player ahead, averages, points per match, matches; on one's own, You and My rank"]
    shell -->|"/org/:slug/matches"| mt["MatchesPage<br/>a card per match, its MVP"]
    mt --> current
    mt -->|GET| mtApi["…/seasons/{n}/matches"]
    shell -->|"/org/:slug/matches/:match?season="| md["MatchPage<br/>MVP, stat leaders, games, overall or one game, averages or totals"]
    md --> current
    md --> table
    md -->|GET| mdApi["…/seasons/{n}/matches/{m}"]
    shell -->|"/org/:slug/admin, adminGuard"| guard[adminGuard<br/>admins and platform admins; others to sign-in or the public pages]
    guard --> sadm["ScoresAdminPage /admin/scores?season=&match=&game=<br/>one game at a time, previous game's players, check bonuses"]
    sadm -->|"GET, PUT, DELETE, /previous-roster, POST bonus-check"| scoresApi["…/admin/seasons/{n}/matches/{m}/games/{g}"]
    sadm -->|GET| sadmApi["…/seasons/{n}/rules, /matches; …/admin/players"]
    sadm --> current
    guard --> padm["PlayersAdminPage /admin/players<br/>add, search, edit inline"]
    padm -->|GET ?q, POST, PUT| playersApi["…/admin/players"]
    padm -->|GET| ranksApi["/api/ranks"]
    padm --> pill
    guard --> seadm["SeasonsAdminPage /admin/seasons<br/>list, edit inline (close, reopen), start the next season"]
    seadm -->|POST, PUT /n| seasonsAdminApi["…/admin/seasons"]
    guard --> scadm["ScoringAdminPage /admin/scoring?season=<br/>Record switches, points per stat, KDA, win, bonuses (add, remove), formula preview, apply to this season or the next only"]
    scadm -->|"GET …/rules, PUT, POST /platform-defaults, POST and DELETE /bonuses"| recordApi["…/admin/seasons/{n}/rules"]
    guard --> madm["MembersAdminPage /admin/members<br/>invite an admin, waiting invites, roles, remove, ownership"]
    madm -->|"GET, PUT, POST, DELETE"| teamApi["…/admin/members, …/admin/invites"]
    guard --> suadm["SignUpsAdminPage /admin/seasons/:season/sign-ups<br/>open or close, who signed up, withdraw, sign someone up"]
    suadm -->|"GET, PUT /signup, POST and DELETE /registrations/{player}"| suAdminApi["…/admin/seasons/{n}/registrations"]
    seadm --> suadm
    guard --> schadm["ScheduleAdminPage /admin/schedule?season=<br/>every match: plan, edit, call off, how full"]
    schadm -->|"GET, PUT"| schApi["…/admin/seasons/{n}/schedule"]
    schadm --> rosadm["RosterAdminPage /admin/matches/:match/roster<br/>confirmed, waiting list, can't make it, no answer; move, write down, places, lock"]
    rosadm -->|"GET, PUT, DELETE, POST"| rosApi["…/admin/matches/{id}/roster, /places, /lock"]
    guard --> cadm["ClaimsAdminPage /admin/claims<br/>Waiting, Accepted, Declined; accept, decline with a reason"]
    guard --> badm["BrandingAdminPage /admin/branding<br/>names, language, logo, accent with its contrast, places per match, social links"]
    badm -->|"PUT, DELETE; then CurrentSeason.show"| brandApi["…/admin/branding, …/admin/logo"]
    seadm --> current
    scadm --> current
    resolver[organizationResolver] -->|GET| orgApi["/api/orgs/{slug}"]
    shell --- resolver
```

| Folder | Holds |
|---|---|
| `shared/` | `ThemeService` (dark or light, the accent's CSS variables), `colour.ts` (WCAG contrast and mixing, the palettes, readable rank colours), `LanguageService` (EN or FR; numbers, dates, plurals and Valorant rank names, such as Immortel, in the language on screen), `OnlineService` (the header's count of open pages), `SessionService` (who is signed in, `canAdmin`, `meOrSignIn` for the guards, one `/api/me` request at a time, Google sign-in with a safe return address, sign-out), `organization.ts` (`organizationResolver`, `fullName`, `initials`), `storage.ts` (local and session storage that survives being blocked), `RankPill`, `riot-id.ts` (`RiotId`, a Riot ID with everything from its first # in a lighter grey, used wherever one is shown in full; `splitRiotId`) |
| `layout/` | `OrgShell` (an organisation's pages), `PlatformShell` (sign-in and Not found), `SiteHeader`, `SiteFooter`, page layout |
| `results/` | The public results: `api.ts` (the API's shapes), `CurrentSeason` (the organisation's seasons, newest first, the one picked with `?season=`, `reload()` after an admin change, `adminApi` (the admin pages' API address), `show()` for the organisation after Branding saves it; its seasons are fetched by slug only), `stats.ts` (stat columns with K, D and A folded into KDA, decimals, the points formula without bonuses nobody can win, Riot ID name and tag), `StandingsTable` (a season's or a match's averages or totals, or one game's lines), `leaderboard/`, `matches/` (`MatchesPage`), `players/` (`PlayersPage`; `players-list.ts`: cards, search, sorts, tiers), `player/` (`PlayerPage`, `barsOf`, `kdaOf`), `match/` (`MatchPage`, `match-stats.ts`: a game's lines as standings, the stat leaders) |
| `admin/` | `adminGuard`, `api.ts` (the admin API's shapes), `players/` (`PlayersAdminPage`: aliases typed as one line, a previous Riot ID keeps its kind), `action.ts` (`AdminAction`: one change at a time, then a status line), `seasons/` (`SeasonsAdminPage`, `problemOf` checking details as the API does), `scoring/` (`ScoringAdminPage`: the rules as typed, `problemOf` and `inputOf`, `previewOf` for the formula, `newBonusProblem` and `newBonusInput`; locked for a finished season), `members/` (`MembersAdminPage`, the invite message to copy), `claims/` (`ClaimsAdminPage`), `sign-ups/` (`SignUpsAdminPage`, `notSignedUp`), `schedule/` (`ScheduleAdminPage`, `planProblem`, `planInput`), `roster/` (`RosterAdminPage`, `notOnRoster`, `MOVES`), `branding/` (`BrandingAdminPage`, `problemOf` and `linkAllowed` checking as the API does), `scores/` (`ScoresAdminPage`; `scores-form.ts`: lines as typed, stat boxes parsed with a decimal comma, an empty place taken from the line's number, the request) |
| `home/` | `HomePage` (the form kept in session storage across Google sign-in), `slugFrom`, `foundingProblem` |
| `platform/` | `PlatformPage`, `platformGuard` (platform admins only; others see Not found), `matching` (search by name, address or owner's email) |
| `account/` | `AccountPage` (Riot ID and Discord for every organisation, typing DELETE to delete), `accountGuard` (signed in only) |
| `claim/` | `ClaimPage` (claim a profile, or create one), `api.ts` (claims' shapes, `matchingPlayers`) |
| `sign-ups/` | `MySeasonsPage`, `api.ts` (sign-ups' shapes, used by the admin page too) |
| `sign-in/` | `SignInPage` |
| `join/` | `JoinPage`, `stepOf` (sign in, accept, wrong account, expired, used, revoked, accepted), `tidyCode` |
| `legal/` | `LegalPage` and `legal-content.ts`: the legal notice, privacy policy, cookie policy and terms, in English and French, one route each |
| `not-found/` | `NotFoundPage` |
| `testing.ts` | Fixtures and helpers shared by the tests (`openAlsace`, `answer`, `text`) |
| `styles.css` | The template's tokens for both themes, Barlow fonts (bundled from `@fontsource`), shared classes (`.panel`, `.pill`, `.heading`, `.lede`, `.label`, buttons) |
| `public/i18n/` | `en.json`, `fr.json` |
| `public/` | `robots.txt`, and `llms.txt` describing the public API for AI agents |

A component used by several features lives in `shared/` (`RankPill`, `RiotId`); one used by several pages of a
single feature lives in that feature (`StandingsTable` in `results/`).

Not on the leaderboard yet: Export CSV and Export poster (phase 11).

## Phases

```mermaid
flowchart LR
    p0[0 Setup ✓] --> p1[1 Schema, import, public API ✓] --> p2[2 Sign-in and admin API ✓]
    p2 --> p3[3 Angular shell and public pages ✓] --> p4[4 Angular admin ✓] --> p5[5 Deploy pipelines ✓] --> p6[6 Cutover ✓]
    p6 --> p7[7 Open the platform<br/>release 2 ✓]
    p7 --> p8[8 Scoring rules editor ✓] --> p9[9 Players and claims ✓] --> p9b[9b Deploys players<br/>don't notice ✓]
    p9b --> p10[10 Seasons, matches<br/>and waiting lists ▶] --> p10b[10b Help]
    p10b --> p11[11 Exports and poster] --> p12[12 Moderation] --> p13[13 Merge tool and scan]
```

✓ done · ▶ next. Until the switch-over (6), every merge into `migration` that passes CI deploys
production, and the Protect migration ruleset lets a pull request merge only once Build and test
(quality gate included) has passed; version tags, a staging copy from `main` and release-please
come with it. The full plan, with each phase's exit criterion, is the migration plan page
kept with the project designs.

Phases 7, 8, 9 and 9b are built. Phase 10 has season sign-ups, creating one's own profile, the
schedule and match rosters with players' answers; not built yet: automatic moves up the waiting
list, notices, deleting an empty organisation (10);
exports (11); moderation (12); merging duplicate players (13).
