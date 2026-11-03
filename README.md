# Tournament Leaderboard

Leaderboards, match stats, schedules and scoring for seasonal Valorant tournaments, run by
organisations such as Alsace Arena's Spike Tournament.

**Live site:** https://alsace-arena-spike-tournament.vercel.app/ (moving to
https://tournament-leaderboard.irmahs.dev)

## Repository layout

| Folder | What it is |
|---|---|
| `legacy/` | The current live app: Next.js 16 on Supabase, deployed by Vercel with Root Directory `legacy`. See [`legacy/README.md`](legacy/README.md). |
| _(coming)_ `backend/`, `frontend/`, `deploy/` | The rewrite: Java 21 + Spring Boot + PostgreSQL (Flyway), Angular, Docker on Hetzner. |

The legacy app keeps running, and keeps taking scores, until the rewrite reaches parity and the
site moves to `tournament-leaderboard.irmahs.dev`.

## Running the legacy app

```bash
cd legacy
npm install
npm run dev
```

It needs `legacy/.env.local`; see [`legacy/README.md`](legacy/README.md) for the variables.

---

Created and run by [Irma HS](https://irmahs.dev) · tournament-leaderboard@irmahs.dev · [MIT licence](LICENSE)
