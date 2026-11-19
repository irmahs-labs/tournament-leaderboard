package dev.irmahs.leaderboard.seasons;

/** How a bonus is won in a game. */
public enum BonusAward {
    /** Goes to the highest value of the bonus's stat. */
    HIGHEST,
    /** Goes to the lowest value of the bonus's stat. */
    LOWEST,
    /** Compares nothing: an admin ticks it for the player they pick. */
    MANUAL,
    /** Goes to the best place (the lowest value of its stat) among the players who won the game: the MVP. */
    TOP_WINNER,
    /** Goes to the best place among the players who lost the game. */
    TOP_LOSER,
    /** Goes to every player whose stat reaches the bonus's threshold. */
    AT_LEAST
}
