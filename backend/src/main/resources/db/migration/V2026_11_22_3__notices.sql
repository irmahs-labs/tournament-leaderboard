-- Notices in the app: matches planned, moved or called off, reminders to answer, moving up from
-- the waiting list, claims answered; and for admins, what other admins changed, the organisation
-- suspended or reactivated, a claim arriving. Only adds a table and a column, so the version
-- before this one keeps working during a deploy.

CREATE TABLE notifications (
  id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id          uuid NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  organization_id  uuid NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  kind             varchar NOT NULL,
  subject          varchar(100),              -- what it's about, such as match:41: a newer notice
                                              -- about it replaces one not shown yet
  payload          jsonb NOT NULL DEFAULT '{}',
  shown_from       timestamptz NOT NULL DEFAULT now(),  -- a match change waits 10 minutes
  read_at          timestamptz
);

CREATE INDEX notifications_by_user ON notifications (user_id, shown_from DESC);

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications FORCE ROW LEVEL SECURITY;
CREATE POLICY organization_isolation ON notifications
  USING (current_organization_id() IS NULL OR organization_id = current_organization_id())
  WITH CHECK (current_organization_id() IS NULL OR organization_id = current_organization_id());

-- Set once the "two days before the reply-by date" reminder went out.
ALTER TABLE matches ADD COLUMN reminded_at timestamptz;
