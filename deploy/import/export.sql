-- Exports the live Supabase tables to CSV, one file per table, in the current directory.
-- Run by export.sh. Read-only: it changes nothing in Supabase.
\set ON_ERROR_STOP on
\copy (SELECT id, rank_name, rank_value FROM public.ranks ORDER BY id) TO 'ranks.csv' WITH (FORMAT csv, HEADER)
\copy (SELECT id, username, current_rank_id FROM public.players ORDER BY username) TO 'players.csv' WITH (FORMAT csv, HEADER)
\copy (SELECT id, match_number, match_date, match_season FROM public.matches ORDER BY id) TO 'matches.csv' WITH (FORMAT csv, HEADER)
\copy (SELECT id, match_id, game_number FROM public.games ORDER BY id) TO 'games.csv' WITH (FORMAT csv, HEADER)
\copy (SELECT id, game_id, player_id, acs, kills, deaths, assists, econ_rating, first_bloods, plants, defuses, win, bonus_fb, bonus_death, bonus_assist, bonus_plant, bonus_defuse FROM public.game_stats ORDER BY id) TO 'game_stats.csv' WITH (FORMAT csv, HEADER)
