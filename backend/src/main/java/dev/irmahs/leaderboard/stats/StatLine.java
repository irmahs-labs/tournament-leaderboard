package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.seasons.Stat;
import java.math.BigDecimal;
import java.util.Map;

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
        Integer clutches,
        Integer place,
        Integer honours) {

    /** A line from stat values; a stat that isn't in the map is empty. Whole stats must be whole. */
    public static StatLine of(Map<Stat, BigDecimal> values) {
        return new StatLine(
                whole(values.get(Stat.PERFORMANCE_SCORE)),
                whole(values.get(Stat.ACS)),
                values.get(Stat.ADR),
                values.get(Stat.DAMAGE_DELTA),
                values.get(Stat.KAST_PCT),
                whole(values.get(Stat.KILLS)),
                whole(values.get(Stat.DEATHS)),
                whole(values.get(Stat.ASSISTS)),
                whole(values.get(Stat.ECON_RATING)),
                whole(values.get(Stat.FIRST_BLOODS)),
                whole(values.get(Stat.PLANTS)),
                whole(values.get(Stat.DEFUSES)),
                values.get(Stat.HEADSHOT_PCT),
                whole(values.get(Stat.CLUTCHES)),
                whole(values.get(Stat.PLACE)),
                whole(values.get(Stat.HONOURS)));
    }

    private static Integer whole(BigDecimal value) {
        return value == null ? null : value.intValueExact();
    }

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
            case PLACE -> whole(place);
            case HONOURS -> whole(honours);
        };
    }

    private static BigDecimal whole(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
