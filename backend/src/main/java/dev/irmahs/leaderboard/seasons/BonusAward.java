package dev.irmahs.leaderboard.seasons;

/** How a bonus is won in a game. */
public enum BonusAward {
    /** Goes to the highest value of the bonus's stat. */
    HIGHEST,
    /** Goes to the lowest value of the bonus's stat. */
    LOWEST,
    /** Compares nothing: an admin ticks it for the player they pick. */
    MANUAL
}
