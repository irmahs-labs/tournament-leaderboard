package dev.irmahs.leaderboard.seasons;

/** The stats a game can record for a player. Each season decides which it records and counts. */
public enum Stat {
    PERFORMANCE_SCORE,
    /** Average combat score. */
    ACS,
    /** Average damage per round. */
    ADR,
    /** Average damage dealt minus damage taken, per round. Negative when a player takes more. */
    DAMAGE_DELTA,
    /** Share of rounds with a kill, an assist, survived or traded. */
    KAST_PCT,
    KILLS,
    DEATHS,
    ASSISTS,
    ECON_RATING,
    FIRST_BLOODS,
    PLANTS,
    DEFUSES,
    HEADSHOT_PCT,
    CLUTCHES
}
