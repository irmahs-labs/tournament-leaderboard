-- Imports the Supabase export (the CSVs in the current directory) into the new schema, under
-- the alsace-arena organisation. All or nothing: one transaction, and a check first that stops
-- on rows the new schema would refuse. It empties alsace-arena's tournament first, so it can run
-- again; its organisation row and its members are kept.
--
-- What maps where:
--   players.username         → players.riot_id; a nickname becomes a NICKNAME alias
--   matches.match_season     → one season per value: 8 matches, 1 to 5 games, 50 per win,
--                              today's stat rules and five bonuses at 10
--   game_stats.acs           → game_stats.performance_score (Valorant's own score); the
--                              tracker's stats, headshot % and clutches stay empty
--   game_stats.bonus_*       → game_stat_bonuses rows, copied as recorded, not recalculated
-- Ids of ranks, players, matches, games and game stats are kept.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE old_ranks (id smallint, rank_name varchar, rank_value smallint) ON COMMIT DROP;
CREATE TEMP TABLE old_players (id uuid, username varchar, nickname text, current_rank_id integer) ON COMMIT DROP;
CREATE TEMP TABLE old_matches (id integer, match_number smallint, match_date date, match_season smallint) ON COMMIT DROP;
CREATE TEMP TABLE old_games (id integer, match_id integer, game_number smallint) ON COMMIT DROP;
CREATE TEMP TABLE old_game_stats (
  id integer, game_id integer, player_id uuid,
  acs integer, kills smallint, deaths smallint, assists smallint, econ_rating integer,
  first_bloods smallint, plants smallint, defuses smallint, win boolean,
  bonus_fb boolean, bonus_death boolean, bonus_assist boolean, bonus_plant boolean, bonus_defuse boolean
) ON COMMIT DROP;

\copy old_ranks FROM 'ranks.csv' WITH (FORMAT csv, HEADER)
\copy old_players FROM 'players.csv' WITH (FORMAT csv, HEADER)
\copy old_matches FROM 'matches.csv' WITH (FORMAT csv, HEADER)
\copy old_games FROM 'games.csv' WITH (FORMAT csv, HEADER)
\copy old_game_stats FROM 'game_stats.csv' WITH (FORMAT csv, HEADER)

-- Rows the new schema refuses. Listed all at once, so one export can be fixed in one go.
DO $$
DECLARE
  problems text;
BEGIN
  SELECT string_agg(problem, E'\n' ORDER BY problem) INTO problems FROM (
    SELECT format('match %s has no season', id)
      FROM old_matches WHERE match_season IS NULL
    UNION ALL
    SELECT format('season %s has match %s more than once (match ids %s)',
                  match_season, match_number, string_agg(id::text, ', ' ORDER BY id))
      FROM old_matches GROUP BY match_season, match_number HAVING count(*) > 1
    UNION ALL
    SELECT format('match %s has game %s more than once (game ids %s)',
                  match_id, game_number, string_agg(id::text, ', ' ORDER BY id))
      FROM old_games GROUP BY match_id, game_number HAVING count(*) > 1
    UNION ALL
    SELECT format('game %s has player %s more than once (stat ids %s)',
                  game_id, player_id, string_agg(id::text, ', ' ORDER BY id))
      FROM old_game_stats GROUP BY game_id, player_id HAVING count(*) > 1
    UNION ALL
    SELECT format('Riot ID %s is used by %s players', lower(username), count(*))
      FROM old_players GROUP BY lower(username) HAVING count(*) > 1
  ) AS found(problem);
  IF problems IS NOT NULL THEN
    RAISE EXCEPTION E'The export has rows the new schema refuses; nothing was imported.\n%', problems;
  END IF;
END
$$;

INSERT INTO organizations (slug, name, tournament_name)
VALUES ('alsace-arena', 'Alsace Arena', 'Spike Tournament')
ON CONFLICT (slug) DO NOTHING;
CREATE TEMP TABLE org ON COMMIT DROP AS SELECT id FROM organizations WHERE slug = 'alsace-arena';

-- Empty a previous import. Deleting matches also deletes their games, stats and awarded
-- bonuses; deleting seasons also deletes their rules and bonuses.
DELETE FROM matches WHERE organization_id = (SELECT id FROM org);
DELETE FROM seasons WHERE organization_id = (SELECT id FROM org);
DELETE FROM players WHERE organization_id = (SELECT id FROM org);

INSERT INTO ranks (id, rank_name, rank_value)
SELECT id, rank_name, rank_value FROM old_ranks
ON CONFLICT (id) DO UPDATE SET rank_name = excluded.rank_name, rank_value = excluded.rank_value;

INSERT INTO seasons (organization_id, number, name, starts_on, match_count, min_games, max_games, points_per_win)
SELECT (SELECT id FROM org), match_season, 'Season ' || match_season, min(match_date),
       greatest(8, max(match_number)), 1, 5, 50
FROM old_matches
GROUP BY match_season;

-- Today's rules. Every stat gets a row, so the Scoring screen shows each one explicitly.
INSERT INTO season_stat_rules (season_id, stat, recorded, effect)
SELECT s.id, r.stat, r.recorded, r.effect
FROM seasons s
CROSS JOIN (VALUES
  ('PERFORMANCE_SCORE', true, 1), ('ACS', false, 0), ('ADR', false, 0), ('DAMAGE_DELTA', false, 0),
  ('KAST_PCT', false, 0), ('KILLS', true, 1), ('DEATHS', true, -1), ('ASSISTS', true, 1),
  ('ECON_RATING', true, 1), ('FIRST_BLOODS', true, 1), ('PLANTS', true, 1), ('DEFUSES', true, 1),
  ('HEADSHOT_PCT', false, 0), ('CLUTCHES', false, 0)
) AS r(stat, recorded, effect)
WHERE s.organization_id = (SELECT id FROM org);

INSERT INTO season_bonuses (season_id, code, label, award, stat, points, position)
SELECT s.id, b.code, b.label, b.award, b.stat, 10, b.position
FROM seasons s
CROSS JOIN (VALUES
  ('FB', 'Most first bloods', 'HIGHEST', 'FIRST_BLOODS', 1),
  ('LD', 'Fewest deaths', 'LOWEST', 'DEATHS', 2),
  ('MA', 'Most assists', 'HIGHEST', 'ASSISTS', 3),
  ('MP', 'Most plants', 'HIGHEST', 'PLANTS', 4),
  ('MD', 'Most defuses', 'HIGHEST', 'DEFUSES', 5)
) AS b(code, label, award, stat, position)
WHERE s.organization_id = (SELECT id FROM org);

INSERT INTO players (id, organization_id, riot_id, current_rank_id)
SELECT id, (SELECT id FROM org), username, current_rank_id FROM old_players;

INSERT INTO player_aliases (player_id, alias, kind)
SELECT id, btrim(nickname), 'NICKNAME'
FROM old_players
WHERE btrim(coalesce(nickname, '')) <> '' AND lower(btrim(nickname)) <> lower(username);

INSERT INTO matches (id, organization_id, season_id, match_number, match_date)
SELECT m.id, s.organization_id, s.id, m.match_number, m.match_date
FROM old_matches m
JOIN seasons s ON s.organization_id = (SELECT id FROM org) AND s.number = m.match_season;

INSERT INTO games (id, match_id, game_number)
SELECT id, match_id, game_number FROM old_games;

INSERT INTO game_stats (id, game_id, player_id, performance_score, kills, deaths, assists,
                        econ_rating, first_bloods, plants, defuses, win)
SELECT id, game_id, player_id, acs, kills, deaths, assists,
       econ_rating, first_bloods, plants, defuses, win
FROM old_game_stats;

INSERT INTO game_stat_bonuses (game_stat_id, bonus_id)
SELECT gs.id, b.id
FROM old_game_stats gs
JOIN games g ON g.id = gs.game_id
JOIN matches m ON m.id = g.match_id
CROSS JOIN LATERAL (VALUES
  ('FB', gs.bonus_fb), ('LD', gs.bonus_death), ('MA', gs.bonus_assist),
  ('MP', gs.bonus_plant), ('MD', gs.bonus_defuse)
) AS flag(code, won)
JOIN season_bonuses b ON b.season_id = m.season_id AND b.code = flag.code
WHERE flag.won;

UPDATE organizations
SET last_activity_at = (SELECT max(match_date)::timestamptz FROM old_matches)
WHERE id = (SELECT id FROM org);

-- The ids were inserted by hand: move each sequence past them, so new rows don't collide.
SELECT setval(pg_get_serial_sequence('ranks', 'id'), coalesce((SELECT max(id) FROM ranks), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('matches', 'id'), coalesce((SELECT max(id) FROM matches), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('games', 'id'), coalesce((SELECT max(id) FROM games), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('game_stats', 'id'), coalesce((SELECT max(id) FROM game_stats), 0) + 1, false);

SELECT (SELECT count(*) FROM players WHERE organization_id = (SELECT id FROM org)) AS players,
       (SELECT count(*) FROM seasons WHERE organization_id = (SELECT id FROM org)) AS seasons,
       (SELECT count(*) FROM matches WHERE organization_id = (SELECT id FROM org)) AS matches,
       (SELECT count(*) FROM old_games) AS games,
       (SELECT count(*) FROM old_game_stats) AS game_stats;

COMMIT;
