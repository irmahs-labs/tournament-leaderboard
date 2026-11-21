-- Season sign-ups: players sign up for a season themselves, with no limit, while an admin keeps
-- sign-ups open; admins sign anyone up, players without an account too, and withdraw people.
-- Only adds a table and columns with defaults, so the version before this one keeps working
-- while both run during a deploy.

ALTER TABLE seasons ADD COLUMN signup_open boolean NOT NULL DEFAULT true;

CREATE TABLE season_registrations (
  season_id        integer NOT NULL REFERENCES seasons(id) ON DELETE CASCADE,
  player_id        uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  organization_id  uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  status           varchar NOT NULL DEFAULT 'REGISTERED'
                   CHECK (status IN ('REGISTERED', 'WITHDRAWN')),
  registered_at    timestamptz NOT NULL DEFAULT now(),
  added_by         uuid REFERENCES app_users(id) ON DELETE SET NULL, -- empty when the player signed up
  PRIMARY KEY (season_id, player_id)
);

CREATE INDEX season_registrations_player ON season_registrations (player_id);

ALTER TABLE season_registrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE season_registrations FORCE ROW LEVEL SECURITY;
CREATE POLICY organization_isolation ON season_registrations
  USING (current_organization_id() IS NULL OR organization_id = current_organization_id())
  WITH CHECK (current_organization_id() IS NULL OR organization_id = current_organization_id());
