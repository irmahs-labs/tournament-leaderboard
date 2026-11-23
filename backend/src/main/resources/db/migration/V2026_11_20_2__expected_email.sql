-- An admin can type a player's Google email in advance: the first person to sign in with it is
-- linked to that player, with no claim to approve. Admins only see it; it's cleared once used.

ALTER TABLE players ADD COLUMN expected_email varchar(254);

CREATE UNIQUE INDEX players_org_expected_email
  ON players (organization_id, lower(expected_email)) WHERE expected_email IS NOT NULL;
