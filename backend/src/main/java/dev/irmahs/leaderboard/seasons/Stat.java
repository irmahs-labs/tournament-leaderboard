package dev.irmahs.leaderboard.seasons;

/** The stats a game can record for a player. Each season decides which it records and counts. */
public enum Stat {
    PERFORMANCE_SCORE(true),
    /** Average combat score. */
    ACS(true),
    /** Average damage per round. */
    ADR(false),
    /** Average damage dealt minus damage taken, per round. Negative when a player takes more. */
    DAMAGE_DELTA(false),
    /** Share of rounds with a kill, an assist, survived or traded. */
    KAST_PCT(false),
    KILLS(true),
    DEATHS(true),
    ASSISTS(true),
    ECON_RATING(true),
    FIRST_BLOODS(true),
    PLANTS(true),
    DEFUSES(true),
    HEADSHOT_PCT(false),
    CLUTCHES(true);

    private final boolean countable;

    Stat(boolean countable) {
        this.countable = countable;
    }

    /**
     * Whether the stat can add to or subtract from a player's points. Per-round averages and
     * percentages can't: adding them up across games means nothing. They can still be shown, and
     * a bonus can still compare them.
     */
    public boolean isCountable() {
        return countable;
    }
}
