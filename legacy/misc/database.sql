-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.ranks (
  id smallint GENERATED ALWAYS AS IDENTITY NOT NULL,
  rank_name character varying NOT NULL DEFAULT ''::character varying,
  rank_value smallint,
  CONSTRAINT ranks_pkey PRIMARY KEY (id)
);
CREATE TABLE public.players (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  username character varying NOT NULL UNIQUE,
  nickname text,
  current_rank_id integer,
  CONSTRAINT players_pkey PRIMARY KEY (id),
  CONSTRAINT players_current_rank_id_fkey FOREIGN KEY (current_rank_id) REFERENCES public.ranks(id)
);
CREATE TABLE public.matches (
  id integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  match_number smallint NOT NULL CHECK (match_number >= 1 AND match_number <= 9),
  match_date date,
  match_season smallint,
  CONSTRAINT matches_pkey PRIMARY KEY (id)
);
CREATE TABLE public.games (
  id integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  match_id integer NOT NULL,
  game_number smallint NOT NULL CHECK (game_number >= 1 AND game_number <= 3),
  CONSTRAINT games_pkey PRIMARY KEY (id),
  CONSTRAINT games_match_id_fkey FOREIGN KEY (match_id) REFERENCES public.matches(id)
);
CREATE TABLE public.profiles (
  id uuid NOT NULL,
  is_admin boolean NOT NULL DEFAULT false,
  CONSTRAINT profiles_pkey PRIMARY KEY (id),
  CONSTRAINT profiles_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id)
);
CREATE TABLE public.game_stats (
  id integer GENERATED ALWAYS AS IDENTITY NOT NULL,
  game_id integer NOT NULL,
  player_id uuid NOT NULL,
  acs integer NOT NULL DEFAULT 0,
  kills smallint NOT NULL DEFAULT 0,
  deaths smallint NOT NULL DEFAULT 0,
  assists smallint NOT NULL DEFAULT 0,
  econ_rating integer NOT NULL DEFAULT 0,
  first_bloods smallint NOT NULL DEFAULT 0,
  plants smallint NOT NULL DEFAULT 0,
  defuses smallint NOT NULL DEFAULT 0,
  win boolean NOT NULL DEFAULT false,
  bonus_death boolean NOT NULL DEFAULT false,
  bonus_assist boolean NOT NULL DEFAULT false,
  bonus_fb boolean NOT NULL DEFAULT false,
  bonus_plant boolean NOT NULL DEFAULT false,
  bonus_defuse boolean NOT NULL DEFAULT false,
  CONSTRAINT game_stats_pkey PRIMARY KEY (id),
  CONSTRAINT game_stats_game_id_fkey FOREIGN KEY (game_id) REFERENCES public.games(id),
  CONSTRAINT game_stats_player_id_fkey FOREIGN KEY (player_id) REFERENCES public.players(id)
);