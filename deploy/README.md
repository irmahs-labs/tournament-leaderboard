# Deploying

The app runs on the shared irmahs.dev server (Hetzner), the same way as the other irmahs-labs
apps: its own folder `/srv/tournament-leaderboard`, its own `compose.prod.yaml`, and the shared
Caddy from [irmahs-labs/proxy](https://github.com/irmahs-labs/proxy) in front.

```
visitor ──HTTPS──▶ Cloudflare ──▶ Caddy (irmahs-labs/proxy) ──"proxy" network──▶ tournament-leaderboard:8080
                                                                                   │ "internal" network
                                                                                   ▼
                                                                                 db (Postgres 17)
```

| File | What it is |
|---|---|
| `Dockerfile` (repo root) | Builds the Spring Boot jar, Angular included, into one image on port 8080 |
| `compose.prod.yaml` (repo root) | Postgres on `internal` only; the app on `internal` and `proxy`, publishing no port |
| `deploy/db-init/01-app-role.sh` | First start only: creates `leaderboard_app`, the role the app connects with |
| `.github/workflows/deploy.yml` | Called by CI after the build and tests pass on a merge into `migration`: SSH in as `deploy`, check out the run's commit, write `.env`, dump the database, `docker compose up --build --wait`, then check the address through Caddy |

## Database roles

- **`leaderboard_owner`**: the Postgres user of the container. Owns the database and the tables;
  Flyway migrates as this role.
- **`leaderboard_app`**: what the app connects with. Reads and writes rows only, so the
  organisation row-level security policies apply to it (they never apply to a table's owner).

Both passwords are read only when the `db_data` volume is created. To change one later, change
the GitHub secret **and** run `ALTER ROLE … PASSWORD '…'` in the database.

## First deploy

1. **Secrets** (GitHub → this repo → Settings → Secrets and variables → Actions), each made with
   `openssl rand -base64 32`:
   - `TOURNAMENT_LEADERBOARD_ADMIN_POSTGRES_PASSWORD`: the admin, `leaderboard_owner`, used by
     Flyway only
   - `TOURNAMENT_LEADERBOARD_POSTGRES_PASSWORD`: the app's own user, `leaderboard_app`

   - `TOURNAMENT_LEADERBOARD_GOOGLE_CLIENT_ID` and `TOURNAMENT_LEADERBOARD_GOOGLE_CLIENT_SECRET`:
     the **production** Google client (Google Cloud → APIs & Services → Credentials, a web
     application with the redirect URI
     `https://tournament-leaderboard.irmahs.dev/login/oauth2/code/google`)

   And two **variables** (the Variables tab next to Secrets), since they're emails rather than
   secrets, kept out of the code:
   - `TOURNAMENT_LEADERBOARD_PLATFORM_ADMINS`: the platform admins' Google emails, separated by
     commas
   - `TOURNAMENT_LEADERBOARD_INITIAL_MEMBERS`: each organisation's first members, as
     `slug:OWNER:email` or `slug:ADMIN:email`, separated by commas. They're added when they first
     sign in; an organisation has one owner at most.

   `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` and `DEPLOY_HOST` come from the organization.
2. **DNS** (Cloudflare): `A` and `AAAA` for `tournament-leaderboard.irmahs.dev`, proxied, like
   the other apps.
3. **Folder**, as admin on the server, if the deploy can't create it:
   `sudo mkdir /srv/tournament-leaderboard && sudo chown deploy:deploy /srv/tournament-leaderboard`
4. **Deploy**: every merge into `migration` that passes CI deploys that commit (Actions → CI, its
   Deploy job). It waits until the app's health check passes, so a broken start fails the run,
   then checks `https://tournament-leaderboard.irmahs.dev/actuator/health` through Caddy, from the
   server: Cloudflare answers 403 to GitHub's runners.
5. **Route the name**: a pull request in irmahs-labs/proxy adding to `caddy/Caddyfile`:

   ```
   # Tournament Leaderboard (irmahs-labs/tournament-leaderboard).
   tournament-leaderboard.irmahs.dev {
   	import common
   	handle {
   		reverse_proxy tournament-leaderboard:8080
   	}
   }
   ```

   Merge it after the DNS records exist; Caddy gets the certificate on the first visit.

## Until the switch-over

Decided on 2026-11-18: every merge into `migration` that passes CI deploys production, as above,
until the rewrite replaces the legacy site. Version tags deploying production, `main` deploying a
staging copy, and release-please come with the switch-over (phase 6), not before.

## Dumps before each deploy

Before starting the new version, the deploy dumps the database with `pg_dump --format=custom`
into `/srv/tournament-leaderboard/backups/` (readable by `deploy` only, ignored by git), named by
date and commit. It keeps the last 14. An empty or failed dump stops the deploy before anything
changes. They're on the same server as the database: they undo a bad migration, not a lost
server; off-server backups are separate.

To restore one, as `deploy` in `/srv/tournament-leaderboard`:

```
export COMPOSE_FILE=compose.prod.yaml
docker compose stop app
docker compose exec -T db pg_restore -U leaderboard_owner -d leaderboard --clean --if-exists < backups/<the dump>
git reset --hard <the dump's commit> && docker compose up -d --build --wait
```
