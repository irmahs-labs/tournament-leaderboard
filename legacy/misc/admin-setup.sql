-- ============================================================================
-- Admin auth setup for Supabase.  Run once in the Supabase SQL editor.
-- The app has no sign-up flow: admins are created by hand, then promoted here.
-- ============================================================================

-- 1. Auto-create a public.profiles row for every new auth user.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Backfill profiles for users that already existed.
insert into public.profiles (id)
select id from auth.users
on conflict (id) do nothing;

-- 2. RLS: a signed-in user may read their OWN profile row.
--    getAdminUser() in src/lib/supabase/auth.ts needs this to see is_admin.
alter table public.profiles enable row level security;

drop policy if exists "read own profile" on public.profiles;
create policy "read own profile"
  on public.profiles for select
  to authenticated
  using (auth.uid() = id);
--    Note: no INSERT/UPDATE/DELETE policies -> clients can never self-promote.
--    is_admin is only ever flipped from the SQL editor (step 4) or the dashboard.

-- 3. Create the admin user:
--    Supabase dashboard -> Authentication -> Users -> "Add user"
--    -> enter email + password, tick "Auto Confirm User".
--    Copy their UUID from the Users list.

-- 4. Promote that user to admin (repeat per admin, with their UUID from step 3).
--    Uses insert-on-conflict rather than a plain update so it works whether or
--    not the trigger in step 1 has already created their profiles row.
insert into public.profiles (id, is_admin)
values ('paste-the-uuid-here', true)
on conflict (id) do update set is_admin = true;

-- 5. Let admins write scores.  The score-entry form inserts/deletes rows in
--    public.games and public.game_stats; without these policies RLS blocks it.
create or replace function public.is_admin()
returns boolean
language sql
security definer
set search_path = ''
stable
as $$
  select exists (
    select 1 from public.profiles where id = auth.uid() and is_admin
  );
$$;

alter table public.games enable row level security;
alter table public.game_stats enable row level security;

-- keep public read working (skip a line if the policy already exists)
drop policy if exists "public read games" on public.games;
create policy "public read games" on public.games for select using (true);

drop policy if exists "public read game_stats" on public.game_stats;
create policy "public read game_stats" on public.game_stats for select using (true);

drop policy if exists "admin write games" on public.games;
create policy "admin write games" on public.games
  for all to authenticated using (public.is_admin()) with check (public.is_admin());

drop policy if exists "admin write game_stats" on public.game_stats;
create policy "admin write game_stats" on public.game_stats
  for all to authenticated using (public.is_admin()) with check (public.is_admin());

-- 6. matches.match_number is UNIQUE on its own, which blocks having match 1 in
--    more than one season. The score form needs (season, match_number) unique
--    instead, so it can create matches for a new season.
alter table public.matches drop constraint if exists matches_match_number_key;
alter table public.matches
  add constraint matches_season_number_key unique (match_season, match_number);

-- admins also need to insert into public.matches (the form auto-creates a match
-- row for a season/number that doesn't exist yet).
alter table public.matches enable row level security;

drop policy if exists "public read matches" on public.matches;
create policy "public read matches" on public.matches for select using (true);

drop policy if exists "admin write matches" on public.matches;
create policy "admin write matches" on public.matches
  for all to authenticated using (public.is_admin()) with check (public.is_admin());

-- 7. The "final match" concept was dropped. is_final is no longer read or written,
--    and match 9 (the old final) no longer exists — a season is matches 1–8.
delete from public.game_stats gs
  using public.games g, public.matches m
  where gs.game_id = g.id and g.match_id = m.id and m.match_number = 9;
delete from public.games g
  using public.matches m
  where g.match_id = m.id and m.match_number = 9;
delete from public.matches where match_number = 9;

alter table public.matches drop column if exists is_final;
alter table public.matches drop constraint if exists matches_match_number_check;
alter table public.matches
  add constraint matches_match_number_check check (match_number >= 1 and match_number <= 8);

-- 8. A match can have a variable number of games (2–5, not always 3).
alter table public.games drop constraint if exists games_game_number_check;
alter table public.games
  add constraint games_game_number_check check (game_number >= 1 and game_number <= 5);

-- 9. The score-entry form can register a brand-new player (username only) inline.
--    Needs public read (unchanged, if it already existed) + an admin insert policy.
alter table public.players enable row level security;

drop policy if exists "public read players" on public.players;
create policy "public read players" on public.players for select using (true);

drop policy if exists "admin insert players" on public.players;
create policy "admin insert players" on public.players
  for insert to authenticated with check (public.is_admin());

-- 10. The player-management page (/admin/players) edits players (username, nickname, rank)
--    too, so the admin write policy needs to cover update as well as insert. Players are
--    never deleted through the app — only created and edited — so there's no delete policy.
drop policy if exists "admin update players" on public.players;
create policy "admin update players" on public.players
  for update to authenticated using (public.is_admin()) with check (public.is_admin());

-- Ranks are reference data (read-only in the app) — admins just need to read them to
-- populate the rank dropdown on the player-management page.
alter table public.ranks enable row level security;
drop policy if exists "public read ranks" on public.ranks;
create policy "public read ranks" on public.ranks for select using (true);
