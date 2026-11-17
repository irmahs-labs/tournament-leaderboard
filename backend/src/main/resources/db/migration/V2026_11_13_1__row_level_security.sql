-- Row-level security: the database's own safety net under the app's organisation filter.
--
-- For a request about one organisation (/api/orgs/{slug}/…), the app sets app.organization_id
-- at the start of each transaction. Rows of another organisation are then invisible, and can't
-- be written. Without the setting (sign-in, /api/me, the platform admin) every row is visible,
-- as those legitimately span organisations.
--
-- It applies to leaderboard_app, the role the app connects with. leaderboard_owner, which runs
-- Flyway and the import, is the database's superuser and isn't affected.

CREATE FUNCTION current_organization_id() RETURNS uuid
LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('app.organization_id', true), '')::uuid $$;

-- The tables that hold an organisation_id. Seasons' rules and bonuses, games and stats belong
-- to a season or a match, which are covered.
DO $$
DECLARE
  scoped text;
BEGIN
  FOREACH scoped IN ARRAY ARRAY[
    'players', 'seasons', 'matches',
    'organization_members', 'organization_invites', 'organization_logos'
  ] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', scoped);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', scoped);
    EXECUTE format($policy$
      CREATE POLICY organization_isolation ON %I
        USING (current_organization_id() IS NULL OR organization_id = current_organization_id())
        WITH CHECK (current_organization_id() IS NULL OR organization_id = current_organization_id())
    $policy$, scoped);
  END LOOP;
END
$$;
