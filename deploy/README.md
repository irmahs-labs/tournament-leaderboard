# Deploying to Hetzner

Production runs on one Hetzner Cloud server (Ubuntu 24.04) with Docker Compose: Caddy for HTTPS,
the app, and Postgres 17. The app image comes from CI (`ghcr.io/irmahs-labs/tournament-leaderboard`,
coming with the deploy pipeline).

| File | What it is |
|---|---|
| `bootstrap.sh` | Run once as root on a new server: users, SSH hardening, Docker, security updates |
| `compose.yaml` | Caddy, the app and Postgres; Postgres has no public port |
| `Caddyfile` | HTTPS for `tournament-leaderboard.irmahs.dev`, proxied to the app |
| `db-init/01-roles.sh` | First start only: creates `leaderboard_owner` (Flyway) and `leaderboard_app` (the app) |
| `.env.example`, `.env.db.example` | The settings and secrets to fill in on the server |

## Setting up a new server

1. In Hetzner: a server with your SSH key, the Cloud Firewall (TCP 22, 80, 443 and UDP 443),
   and `A`/`AAAA` records for `tournament-leaderboard.irmahs.dev` (DNS only, not proxied).
2. Prepare the server, replacing `irma` with your user name:

   ```bash
   scp deploy/bootstrap.sh root@SERVER_IP:
   ssh root@SERVER_IP 'bash bootstrap.sh irma'
   ```

   Before closing that session, check `ssh irma@SERVER_IP` works: root can't sign in afterwards.
3. Copy the deploy files and create the two secret files from their examples:

   ```bash
   scp -r deploy/compose.yaml deploy/Caddyfile deploy/db-init deploy@SERVER_IP:/opt/leaderboard/
   ssh deploy@SERVER_IP
   cd /opt/leaderboard
   # write .env and .env.db from the examples in the repo, then:
   chmod 600 .env .env.db
   ```

4. Start it once the app image exists: `docker compose up -d`.

`db-init` only runs when the `pgdata` volume is empty. Changing `.env.db` later doesn't change
the passwords in the database; change them with `ALTER ROLE` too.
