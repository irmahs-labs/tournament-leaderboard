-- Automatic moves up the waiting list: when a place frees up, the first player waiting takes it,
-- unless the admins turned it off for that match to decide by hand. Only adds a column with a
-- default, so the version before this one keeps working during a deploy.

ALTER TABLE matches ADD COLUMN auto_promote boolean NOT NULL DEFAULT true;
