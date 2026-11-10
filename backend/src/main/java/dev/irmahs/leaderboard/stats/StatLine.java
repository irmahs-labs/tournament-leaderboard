package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.seasons.Stat;
import java.math.BigDecimal;

/**
 * One player's numbers in one game. A stat is empty when its season didn't record it at the
 * time the game was saved.
 */
public record StatLine(
        Integer performanceScore,
        Integer acs,
        BigDecimal adr,
        BigDecimal damageDelta,
        BigDecimal kastPct,
        Integer kills,
        Integer deaths,
        Integer assists,
        Integer econRating,
        Integer firstBloods,
        Integer plants,
        Integer defuses,
        BigDecimal headshotPct,
        Integer clutches) {

    /** The value of one stat, or nothing when the game didn't record it. */
    public BigDecimal value(Stat stat) {
        return switch (stat) {
            case PERFORMANCE_SCORE -> whole(performanceScore);
            case ACS -> whole(acs);
            case ADR -> adr;
            case DAMAGE_DELTA -> damageDelta;
            case KAST_PCT -> kastPct;
            case KILLS -> whole(kills);
            case DEATHS -> whole(deaths);
            case ASSISTS -> whole(assists);
            case ECON_RATING -> whole(econRating);
            case FIRST_BLOODS -> whole(firstBloods);
            case PLANTS -> whole(plants);
            case DEFUSES -> whole(defuses);
            case HEADSHOT_PCT -> headshotPct;
            case CLUTCHES -> whole(clutches);
        };
    }

    private static BigDecimal whole(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
