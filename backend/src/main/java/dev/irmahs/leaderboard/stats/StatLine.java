package dev.irmahs.leaderboard.stats;

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
        Integer clutches) {}
