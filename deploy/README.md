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
| `.github/workflows/deploy.yml` | SSH in as `deploy`, update the code, write `.env`, `docker compose up --build --wait` |

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
   - `TOURNAMENT_LEADERBOARD_POSTGRES_PASSWORD`: `leaderboard_owner`'s password
   - `TOURNAMENT_LEADERBOARD_APP_DB_PASSWORD`: `leaderboard_app`'s password

   `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` and `DEPLOY_HOST` come from the organization.
2. **DNS** (Cloudflare): `A` and `AAAA` for `tournament-leaderboard.irmahs.dev`, proxied, like
   the other apps.
3. **Folder**, as admin on the server, if the deploy can't create it:
   `sudo mkdir /srv/tournament-leaderboard && sudo chown deploy:deploy /srv/tournament-leaderboard`
4. **Deploy**: Actions → Deploy → Run workflow, on the branch or tag to deploy. It waits until
   the app's health check passes, so a broken start fails the run.
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

## Running it locally

```bash
docker network create proxy   # once; on the server it already exists
printf 'POSTGRES_PASSWORD=local-owner\nAPP_DB_PASSWORD=local-app\n' > .env
docker compose -f compose.prod.yaml up -d --build --wait
```

The app has no published port here either; use `cd backend && ../mvnw spring-boot:run` for
everyday development.
