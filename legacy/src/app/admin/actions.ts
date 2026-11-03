'use server';

import { revalidatePath } from 'next/cache';
import { cookies } from 'next/headers';
import { createClient } from '@/lib/supabase/server';
import { getAdminUser } from '@/lib/supabase/auth';

export type SaveRow = {
  /** Existing player's id. Empty when this row is a brand-new player — see `new_player`. */
  player_id: string;
  new_player?: { username: string };
  acs: number;
  kills: number;
  deaths: number;
  assists: number;
  econ_rating: number;
  first_bloods: number;
  plants: number;
  defuses: number;
  win: boolean;
  bonus_fb: boolean;
  bonus_death: boolean;
  bonus_assist: boolean;
  bonus_plant: boolean;
  bonus_defuse: boolean;
};

export type SaveResult = { ok: boolean; error: string | null };

export type LoadedRow = {
  player_id: string;
  acs: number;
  kills: number;
  deaths: number;
  assists: number;
  econ_rating: number;
  first_bloods: number;
  plants: number;
  defuses: number;
  win: boolean;
  bonus_fb: boolean;
  bonus_death: boolean;
  bonus_assist: boolean;
  bonus_plant: boolean;
  bonus_defuse: boolean;
};

/** Looks up (never creates) the games.id for a (season, match_number, game_number), if it exists. */
async function findGameId(
  supabase: ReturnType<typeof createClient>,
  season: number,
  matchNumber: number,
  gameNumber: number,
): Promise<{ gameId: number | null; error: string | null }> {
  const { data: match, error: matchErr } = await supabase
    .from('matches')
    .select('id')
    .eq('match_season', season)
    .eq('match_number', matchNumber)
    .maybeSingle();
  if (matchErr) return { gameId: null, error: matchErr.message };
  if (!match) return { gameId: null, error: null };

  const { data: game, error: gameErr } = await supabase
    .from('games')
    .select('id')
    .eq('match_id', match.id)
    .eq('game_number', gameNumber)
    .maybeSingle();
  if (gameErr) return { gameId: null, error: gameErr.message };
  return { gameId: game?.id ?? null, error: null };
}

/** Loads the player rows already saved for a (season, match, game), for editing. Empty if none exist yet. */
export async function loadGameStats(input: {
  season: number;
  matchNumber: number;
  gameNumber: number;
}): Promise<{ rows: LoadedRow[]; error: string | null }> {
  if (!(await getAdminUser())) return { rows: [], error: 'Not authorized.' };

  const { season, matchNumber, gameNumber } = input;
  if (!season || !matchNumber || !gameNumber) return { rows: [], error: null };

  const supabase = createClient(await cookies());
  const { gameId, error: findErr } = await findGameId(supabase, season, matchNumber, gameNumber);
  if (findErr) return { rows: [], error: findErr };
  if (!gameId) return { rows: [], error: null };

  const { data, error } = await supabase
    .from('game_stats')
    .select(
      'player_id, acs, kills, deaths, assists, econ_rating, first_bloods, plants, defuses, win, bonus_fb, bonus_death, bonus_assist, bonus_plant, bonus_defuse',
    )
    .eq('game_id', gameId)
    .order('id');
  if (error) return { rows: [], error: error.message };

  return { rows: (data ?? []) as LoadedRow[], error: null };
}

/**
 * A match's games usually share the same 10 players. For a game that has no saved stats yet,
 * this returns the player roster (in save order) from the nearest earlier game in the same
 * match that does have stats — so switching from game 1 to game 2 carries the names over and
 * only the numbers need re-entering. Empty if there's no earlier game with data.
 */
export async function loadPreviousGamePlayers(input: {
  season: number;
  matchNumber: number;
  beforeGameNumber: number;
}): Promise<{ playerIds: string[]; error: string | null }> {
  if (!(await getAdminUser())) return { playerIds: [], error: 'Not authorized.' };

  const { season, matchNumber, beforeGameNumber } = input;
  if (!season || !matchNumber || beforeGameNumber <= 1) return { playerIds: [], error: null };

  const supabase = createClient(await cookies());
  const { data: match, error: matchErr } = await supabase
    .from('matches')
    .select('id')
    .eq('match_season', season)
    .eq('match_number', matchNumber)
    .maybeSingle();
  if (matchErr) return { playerIds: [], error: matchErr.message };
  if (!match) return { playerIds: [], error: null };

  const { data: games, error: gamesErr } = await supabase
    .from('games')
    .select('id')
    .eq('match_id', match.id)
    .lt('game_number', beforeGameNumber)
    .order('game_number', { ascending: false });
  if (gamesErr) return { playerIds: [], error: gamesErr.message };
  if (!games || games.length === 0) return { playerIds: [], error: null };

  for (const g of games) {
    const { data: stats, error: statsErr } = await supabase
      .from('game_stats')
      .select('player_id')
      .eq('game_id', g.id)
      .order('id');
    if (statsErr) return { playerIds: [], error: statsErr.message };
    if (stats && stats.length > 0) {
      return { playerIds: stats.map((s) => s.player_id), error: null };
    }
  }
  return { playerIds: [], error: null };
}

/** Deletes a saved game (and its stats) for a (season, match, game). No-op if it doesn't exist. */
export async function deleteGameStats(input: {
  season: number;
  matchNumber: number;
  gameNumber: number;
}): Promise<SaveResult> {
  if (!(await getAdminUser())) return { ok: false, error: 'Not authorized.' };

  const { season, matchNumber, gameNumber } = input;
  if (!season || !matchNumber || !gameNumber) {
    return { ok: false, error: 'Pick a season, match and game.' };
  }

  const supabase = createClient(await cookies());
  const { gameId, error: findErr } = await findGameId(supabase, season, matchNumber, gameNumber);
  if (findErr) return { ok: false, error: findErr };
  if (!gameId) return { ok: true, error: null }; // nothing to delete

  const { error: delStatsErr } = await supabase.from('game_stats').delete().eq('game_id', gameId);
  if (delStatsErr) return { ok: false, error: delStatsErr.message };

  const { error: delGameErr } = await supabase.from('games').delete().eq('id', gameId);
  if (delGameErr) return { ok: false, error: delGameErr.message };

  revalidatePath('/', 'layout');
  return { ok: true, error: null };
}

export type PlayerInput = {
  username: string;
  nickname?: string | null;
  current_rank_id?: number | null;
};

/** Registers a brand-new player. Fails if the username is already taken. */
export async function createPlayer(input: PlayerInput): Promise<SaveResult> {
  if (!(await getAdminUser())) return { ok: false, error: 'Not authorized.' };

  const username = input.username.trim();
  if (!username) return { ok: false, error: 'Username is required.' };

  const supabase = createClient(await cookies());
  const { error } = await supabase.from('players').insert({
    username,
    nickname: input.nickname?.trim() || null,
    current_rank_id: input.current_rank_id ?? null,
  });
  if (error) {
    if (error.code === '23505') return { ok: false, error: 'That username is already taken.' };
    return { ok: false, error: error.message };
  }

  revalidatePath('/', 'layout');
  return { ok: true, error: null };
}

/** Updates an existing player's username, nickname, and rank. Fails if the username is taken. */
export async function updatePlayer(input: PlayerInput & { id: string }): Promise<SaveResult> {
  if (!(await getAdminUser())) return { ok: false, error: 'Not authorized.' };

  const username = input.username.trim();
  if (!username) return { ok: false, error: 'Username is required.' };

  const supabase = createClient(await cookies());
  const { error } = await supabase
    .from('players')
    .update({
      username,
      nickname: input.nickname?.trim() || null,
      current_rank_id: input.current_rank_id ?? null,
    })
    .eq('id', input.id);
  if (error) {
    if (error.code === '23505') return { ok: false, error: 'That username is already taken.' };
    return { ok: false, error: error.message };
  }

  revalidatePath('/', 'layout');
  return { ok: true, error: null };
}

export async function saveGameStats(input: {
  season: number;
  matchNumber: number;
  gameNumber: number;
  rows: SaveRow[];
}): Promise<SaveResult> {
  if (!(await getAdminUser())) return { ok: false, error: 'Not authorized.' };

  const { season, matchNumber, gameNumber, rows } = input;
  if (!season || !matchNumber || !gameNumber) {
    return { ok: false, error: 'Pick a season, match and game.' };
  }
  if (matchNumber < 1 || matchNumber > 8 || gameNumber < 1 || gameNumber > 5) {
    return { ok: false, error: 'Match must be 1–8 and game 1–5.' };
  }
  if (rows.length === 0) return { ok: false, error: 'Nothing to save.' };
  if (rows.some((r) => !r.player_id && !r.new_player?.username.trim())) {
    return { ok: false, error: 'Every row needs a player picked, or a new player’s username.' };
  }

  const supabase = createClient(await cookies());

  // Create any brand-new players first, resolving every row to a player_id.
  const resolved: SaveRow[] = [];
  for (const r of rows) {
    if (r.player_id) {
      resolved.push(r);
      continue;
    }
    const { data: createdPlayer, error: createPlayerErr } = await supabase
      .from('players')
      .insert({ username: r.new_player!.username.trim() })
      .select('id')
      .single();
    if (createPlayerErr || !createdPlayer) {
      return {
        ok: false,
        error: createPlayerErr?.message ?? `Could not create player "${r.new_player!.username}".`,
      };
    }
    resolved.push({ ...r, player_id: createdPlayer.id });
  }

  const ids = resolved.map((r) => r.player_id);
  if (new Set(ids).size !== ids.length) {
    return { ok: false, error: 'The same player is used on more than one row.' };
  }

  // Find or create the match row for this (season, match_number).
  let matchId: number;
  const { data: match, error: matchErr } = await supabase
    .from('matches')
    .select('id')
    .eq('match_season', season)
    .eq('match_number', matchNumber)
    .maybeSingle();
  if (matchErr) return { ok: false, error: matchErr.message };

  if (match) {
    matchId = match.id;
  } else {
    const { data: createdMatch, error: createMatchErr } = await supabase
      .from('matches')
      .insert({ match_season: season, match_number: matchNumber })
      .select('id')
      .single();
    if (createMatchErr || !createdMatch) {
      return {
        ok: false,
        error:
          createMatchErr?.message ??
          `Season ${season} match ${matchNumber} does not exist and could not be created.`,
      };
    }
    matchId = createdMatch.id;
  }

  // Find or create the games row for this (match, game_number).
  let gameId: number;
  const { data: existing, error: findErr } = await supabase
    .from('games')
    .select('id')
    .eq('match_id', matchId)
    .eq('game_number', gameNumber)
    .maybeSingle();
  if (findErr) return { ok: false, error: findErr.message };

  if (existing) {
    gameId = existing.id;
    // Re-entry replaces the previous stats for this game.
    const { error: delErr } = await supabase.from('game_stats').delete().eq('game_id', gameId);
    if (delErr) return { ok: false, error: delErr.message };
  } else {
    const { data: created, error: createErr } = await supabase
      .from('games')
      .insert({ match_id: matchId, game_number: gameNumber })
      .select('id')
      .single();
    if (createErr || !created) return { ok: false, error: createErr?.message ?? 'Could not create the game.' };
    gameId = created.id;
  }

  const { error: insertErr } = await supabase.from('game_stats').insert(
    resolved.map((r) => ({
      game_id: gameId,
      player_id: r.player_id,
      acs: r.acs,
      kills: r.kills,
      deaths: r.deaths,
      assists: r.assists,
      econ_rating: r.econ_rating,
      first_bloods: r.first_bloods,
      plants: r.plants,
      defuses: r.defuses,
      win: r.win,
      bonus_fb: r.bonus_fb,
      bonus_death: r.bonus_death,
      bonus_assist: r.bonus_assist,
      bonus_plant: r.bonus_plant,
      bonus_defuse: r.bonus_defuse,
    })),
  );
  if (insertErr) return { ok: false, error: insertErr.message };

  revalidatePath('/', 'layout');
  return { ok: true, error: null };
}
