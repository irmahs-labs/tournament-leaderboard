-- Players claim the player record admins already enter scores for; an admin accepts or declines.
-- Accepting links the member to the player (organization_members.player_id).

CREATE TABLE player_claims (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  player_id        uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  user_id          uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  note             varchar(200),
  status           varchar NOT NULL DEFAULT 'PENDING'
                   CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
  reason           varchar(200),                 -- why it was declined, shown to the player
  self_approved    boolean NOT NULL DEFAULT false, -- the only admin accepted their own claim
  decided_by       uuid REFERENCES app_users(id) ON DELETE SET NULL,
  created_at       timestamptz NOT NULL DEFAULT now(),
  decided_at       timestamptz
);

-- One claim waiting per member at a time.
CREATE UNIQUE INDEX one_waiting_claim_per_member
  ON player_claims (organization_id, user_id) WHERE status = 'PENDING';
CREATE INDEX player_claims_queue ON player_claims (organization_id, status, created_at);

ALTER TABLE player_claims ENABLE ROW LEVEL SECURITY;
ALTER TABLE player_claims FORCE ROW LEVEL SECURITY;
CREATE POLICY organization_isolation ON player_claims
  USING (current_organization_id() IS NULL OR organization_id = current_organization_id())
  WITH CHECK (current_organization_id() IS NULL OR organization_id = current_organization_id());
