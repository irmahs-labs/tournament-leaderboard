-- Contract step for V2026_11_20_7: season sign-ups were replaced by answering each match
-- (match_attendance), and no version since #79 reads seasons.signup_open or
-- season_registrations, so the version running beside this one during a deploy doesn't miss them.

DROP TABLE season_registrations;

ALTER TABLE seasons DROP COLUMN signup_open;
