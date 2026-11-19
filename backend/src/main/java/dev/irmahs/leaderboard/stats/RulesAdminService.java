package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.scoring.BonusCalculator;
import dev.irmahs.leaderboard.scoring.BonusCalculator.Entry;
import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.SeasonService;
import dev.irmahs.leaderboard.seasons.SeasonService.RulesView;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Scoring screen: what each stat, a win, each game's KDA ratio and each bonus are worth in a
 * season, and which stats it records. A change applies to the whole season at once: points are
 * computed when they're read, and the automatic bonuses of every saved game are worked out again
 * here. Manual bonuses stay as the admins ticked them. A finished season's rules can't change, so
 * its results stay as they were.
 */
@Service
@Transactional
public class RulesAdminService {

    static final BigDecimal MAX_POINTS = BigDecimal.valueOf(1000);
    static final int MAX_BONUS_POINTS = 1000;
    static final BigDecimal MAX_THRESHOLD = BigDecimal.valueOf(40);
    private static final int DECIMALS = 2;

    private final SeasonService seasons;
    private final GameStatRepository lines;
    private final BonusCalculator bonuses;

    RulesAdminService(SeasonService seasons, GameStatRepository lines, BonusCalculator bonuses) {
        this.seasons = seasons;
        this.lines = lines;
        this.bonuses = bonuses;
    }

    /**
     * Changes the season's rules. What the request leaves out stays as it is.
     *
     * @throws ResponseStatusException 400 when a value is wrong, 409 when the season is finished;
     *     nothing changes then
     */
    public RulesView save(String slug, int number, RulesInput input) {
        Season season = requireOpen(slug, number);
        if (input.stats() != null) {
            for (StatRuleInput rule : input.stats()) {
                if (rule == null || rule.stat() == null) {
                    throw badRequest("Each stat rule needs its stat");
                }
                BigDecimal points = rule.points() == null
                        ? season.getStatRules().get(rule.stat()).points()
                        : checkPoints(rule.stat() + "'s points", rule.points(), MAX_POINTS.negate());
                if (points.signum() != 0 && !rule.stat().isCountable()) {
                    throw badRequest(rule.stat() + " can be shown but can't count towards points");
                }
                season.setStatRule(rule.stat(), new StatRule(rule.recorded(), points));
            }
        }
        int pointsPerWin = season.getPointsPerWin();
        if (input.pointsPerWin() != null) {
            pointsPerWin = checkPoints("The points per win", BigDecimal.valueOf(input.pointsPerWin()), BigDecimal.ZERO)
                    .intValueExact();
        }
        BigDecimal kdaPoints = input.kdaPoints() == null
                ? season.getKdaPoints()
                : checkPoints("The KDA points", input.kdaPoints(), BigDecimal.ZERO);
        season.setPoints(pointsPerWin, kdaPoints);
        if (input.bonuses() != null) {
            for (BonusInput change : input.bonuses()) {
                changeBonus(season, change);
            }
        }
        reaward(season);
        return seasons.rules(slug, number);
    }

    /**
     * Gives the season the platform's rules, keeping its other bonuses.
     *
     * @throws ResponseStatusException 409 when the season is finished
     */
    public RulesView usePlatformDefaults(String slug, int number) {
        Season season = requireOpen(slug, number);
        season.usePlatformDefaults();
        reaward(season);
        return seasons.rules(slug, number);
    }

    private Season requireOpen(String slug, int number) {
        Season season = seasons.require(slug, number);
        if (season.getFinishedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, season.getName() + " is finished: reopen it to change its rules");
        }
        return season;
    }

    private static void changeBonus(Season season, BonusInput change) {
        if (change == null || change.code() == null) {
            throw badRequest("Each bonus needs its code");
        }
        SeasonBonus bonus = season.bonus(change.code())
                .orElseThrow(() -> badRequest(season.getName() + " has no bonus " + change.code()));
        int points = change.points() == null
                ? bonus.getPoints()
                : checkPoints(bonus.getCode() + "'s points", BigDecimal.valueOf(change.points()), BigDecimal.ZERO)
                        .intValueExact();
        BigDecimal threshold = bonus.getThreshold();
        if (bonus.getAward() == BonusAward.AT_LEAST && change.threshold() != null) {
            threshold = change.threshold();
            if (threshold.stripTrailingZeros().scale() > DECIMALS
                    || threshold.signum() < 0
                    || threshold.compareTo(MAX_THRESHOLD) > 0) {
                throw badRequest(bonus.getCode() + "'s threshold must be between 0 and " + MAX_THRESHOLD);
            }
        } else if (change.threshold() != null) {
            throw badRequest(bonus.getCode() + " has no threshold");
        }
        bonus.change(points, threshold);
    }

    /** Works out the automatic bonuses of every saved game of the season again. */
    private void reaward(Season season) {
        lines.flush();
        Map<Integer, List<GameStat>> byGame = lines.findBySeason(season).stream()
                .collect(Collectors.groupingBy(line -> line.getGame().getId()));
        for (List<GameStat> game : byGame.values()) {
            Map<GameStat, Entry> entries = new LinkedHashMap<>();
            game.forEach(line -> entries.put(line, new Entry(line.getLine(), line.isWin())));
            Map<GameStat, Set<SeasonBonus>> awarded = bonuses.award(season, entries);
            awarded.forEach(GameStat::reaward);
        }
    }

    private static BigDecimal checkPoints(String what, BigDecimal value, BigDecimal min) {
        if (value.stripTrailingZeros().scale() > DECIMALS) {
            throw badRequest(what + " have at most " + DECIMALS + " decimals");
        }
        if (value.compareTo(min) < 0 || value.compareTo(MAX_POINTS) > 0) {
            throw badRequest(what + " must be between " + min.toPlainString() + " and " + MAX_POINTS);
        }
        return value;
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * @param recorded whether admins are asked for it; switched off, it stops counting in every game
     *     of the season
     * @param points what each unit is worth, negative to subtract; empty keeps what it is
     */
    public record StatRuleInput(Stat stat, boolean recorded, BigDecimal points) {}

    /**
     * @param points what the bonus is worth; empty keeps what it is
     * @param threshold for an "at least" bonus, the value to reach; empty keeps what it is
     */
    public record BonusInput(String code, Integer points, BigDecimal threshold) {}

    /**
     * @param stats the stats to change; the others stay as they are
     * @param kdaPoints what each game's KDA ratio is worth
     * @param bonuses the bonuses to change, by code; the others stay as they are
     */
    public record RulesInput(
            List<StatRuleInput> stats, Integer pointsPerWin, BigDecimal kdaPoints, List<BonusInput> bonuses) {}
}
