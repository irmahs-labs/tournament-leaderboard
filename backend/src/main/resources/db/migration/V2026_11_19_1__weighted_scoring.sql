-- Weighted scoring. Riot's custom games no longer show the performance score or economy, so
-- seasons now give each stat its own number of points instead of only adding or subtracting it,
-- and gain what replaces those two stats:
--   * points per stat (kills 1, assists 1.5, first bloods 4, …), replacing effect (-1, 0, 1);
--   * points for each game's KDA ratio, (kills + assists) / max(deaths, 1);
--   * the player's place on the in-game scoreboard, which picks each team's best player
--     (MVP on the winning team, SVP on the losing one);
--   * honours received, and a bonus for receiving at least a set number of them.
-- Existing seasons keep exactly the points they had: each stat's points are its old effect, and
-- their KDA counts for nothing.

-- Points per stat, from the old effect.
ALTER TABLE season_stat_rules ADD COLUMN points numeric(6, 2);
UPDATE season_stat_rules SET points = effect;
ALTER TABLE season_stat_rules
  ALTER COLUMN points SET NOT NULL,
  ADD CONSTRAINT season_stat_rules_points_check CHECK (points BETWEEN -1000 AND 1000),
  DROP COLUMN effect;

-- Two new stats: the scoreboard place, and honours received.
ALTER TABLE season_stat_rules DROP CONSTRAINT season_stat_rules_stat_check;
ALTER TABLE season_stat_rules ADD CONSTRAINT season_stat_rules_stat_check
  CHECK (stat IN ('PERFORMANCE_SCORE', 'ACS', 'ADR', 'DAMAGE_DELTA', 'KAST_PCT', 'KILLS', 'DEATHS',
                  'ASSISTS', 'ECON_RATING', 'FIRST_BLOODS', 'PLANTS', 'DEFUSES', 'HEADSHOT_PCT',
                  'CLUTCHES', 'PLACE', 'HONOURS'));
INSERT INTO season_stat_rules (season_id, stat, recorded, points)
SELECT s.id, new_stat.stat, false, 0
FROM seasons s
CROSS JOIN (VALUES ('PLACE'), ('HONOURS')) AS new_stat(stat);

ALTER TABLE game_stats
  ADD COLUMN place smallint CHECK (place BETWEEN 1 AND 40),   -- 1 is the top of the scoreboard
  ADD COLUMN honours smallint CHECK (honours BETWEEN 0 AND 40);

-- Points for the KDA ratio of each game.
ALTER TABLE seasons ADD COLUMN kda_points numeric(6, 2) NOT NULL DEFAULT 0
  CHECK (kda_points BETWEEN 0 AND 1000);

-- Three new ways to win a bonus: best place on the winning team, best place on the losing team,
-- and a stat at or above a threshold.
ALTER TABLE season_bonuses DROP CONSTRAINT season_bonuses_award_check;
ALTER TABLE season_bonuses ADD CONSTRAINT season_bonuses_award_check
  CHECK (award IN ('HIGHEST', 'LOWEST', 'MANUAL', 'TOP_WINNER', 'TOP_LOSER', 'AT_LEAST'));
ALTER TABLE season_bonuses DROP CONSTRAINT season_bonuses_stat_check;
ALTER TABLE season_bonuses ADD CONSTRAINT season_bonuses_stat_check
  CHECK (stat IN ('PERFORMANCE_SCORE', 'ACS', 'ADR', 'DAMAGE_DELTA', 'KAST_PCT', 'KILLS', 'DEATHS',
                  'ASSISTS', 'ECON_RATING', 'FIRST_BLOODS', 'PLANTS', 'DEFUSES', 'HEADSHOT_PCT',
                  'CLUTCHES', 'PLACE', 'HONOURS'));
ALTER TABLE season_bonuses ADD COLUMN threshold numeric(6, 2);
ALTER TABLE season_bonuses ADD CONSTRAINT season_bonuses_threshold_check
  CHECK ((award = 'AT_LEAST') = (threshold IS NOT NULL) AND (threshold IS NULL OR threshold >= 0));
ALTER TABLE season_bonuses ADD CONSTRAINT season_bonuses_points_check CHECK (points BETWEEN 0 AND 1000);
