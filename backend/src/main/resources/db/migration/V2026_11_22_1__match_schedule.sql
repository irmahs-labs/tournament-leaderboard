-- The schedule and match rosters: admins plan each match (day, time, where, reply-by date,
-- places), and players say "I'm in" or "Can't make it". Once a match is full, "I'm in" joins its
-- waiting list. Only adds columns with defaults and a table, so the version before this one keeps
-- working while both run during a deploy.

ALTER TABLE matches
  ADD COLUMN start_time  time,
  ADD COLUMN location    varchar(200),                   -- a venue address or a Discord channel
  ADD COLUMN reply_by    date,
  ADD COLUMN places      smallint NOT NULL DEFAULT 10 CHECK (places BETWEEN 2 AND 40),
  ADD COLUMN locked_at   timestamptz,                    -- set: nobody's answer changes any more
  ADD COLUMN status      varchar NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'CANCELLED'));

-- The matches already there start from their organisation's players per match.
UPDATE matches SET places = organizations.players_per_match
  FROM organizations WHERE organizations.id = matches.organization_id;

CREATE TABLE match_attendance (
  match_id         integer NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  player_id        uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  organization_id  uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  status           varchar NOT NULL CHECK (status IN ('CONFIRMED', 'WAITING', 'DECLINED')),
  source           varchar NOT NULL DEFAULT 'APP' CHECK (source IN ('APP', 'ADMIN', 'WAITING_LIST')),
  queued_at        timestamptz,                         -- the waiting list's order
  answered_at      timestamptz NOT NULL DEFAULT now(),
  answered_by      uuid REFERENCES app_users(id) ON DELETE SET NULL,
  PRIMARY KEY (match_id, player_id)
);

CREATE INDEX match_waiting_list ON match_attendance (match_id, queued_at) WHERE status = 'WAITING';
CREATE INDEX match_attendance_player ON match_attendance (player_id);

ALTER TABLE match_attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE match_attendance FORCE ROW LEVEL SECURITY;
CREATE POLICY organization_isolation ON match_attendance
  USING (current_organization_id() IS NULL OR organization_id = current_organization_id())
  WITH CHECK (current_organization_id() IS NULL OR organization_id = current_organization_id());
