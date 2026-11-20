-- A person's Riot ID and Discord username belong to their account: every player profile linked
-- to it shows the same ones, and changing them on My account changes all of them. Only the
-- stats, the rank and the aliases stay each organisation's own.
ALTER TABLE app_users
  ADD COLUMN riot_id varchar(100),
  ADD COLUMN discord_username varchar(100);

-- Accounts already linked take them from their profile (the first one, when there are several).
UPDATE app_users u
SET riot_id = p.riot_id, discord_username = p.discord_username
FROM (
  SELECT DISTINCT ON (m.user_id) m.user_id, p.riot_id, p.discord_username
  FROM organization_members m
  JOIN players p ON p.id = m.player_id
  ORDER BY m.user_id, m.joined_at
) p
WHERE p.user_id = u.id;
