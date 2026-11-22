package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.events.AdminChanged;
import dev.irmahs.leaderboard.scoring.BonusCalculator;
import dev.irmahs.leaderboard.scoring.BonusCalculator.Entry;
import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.NextSeasonRules;
import dev.irmahs.leaderboard.seasons.RulesSnapshot;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.SeasonService;
import dev.irmahs.leaderboard.seasons.SeasonService.RulesView;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
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
    static final int MAX_LABEL = 40;
    private static final int MAX_CODE = 4;

    /** The ways an admin's own bonus can be won; MVP and SVP are the platform's. */
    private static final Set<BonusAward> CUSTOM_AWARDS =
            Set.of(BonusAward.HIGHEST, BonusAward.LOWEST, BonusAward.AT_LEAST, BonusAward.MANUAL);

    private final SeasonService seasons;
    private final GameStatRepository lines;
    private final BonusCalculator bonuses;
    private final NextSeasonRules nextSeason;
    private final ApplicationEventPublisher events;

    RulesAdminService(
            SeasonService seasons,
            GameStatRepository lines,
            BonusCalculator bonuses,
            NextSeasonRules nextSeason,
            ApplicationEventPublisher events) {
        this.seasons = seasons;
        this.lines = lines;
        this.bonuses = bonuses;
        this.nextSeason = nextSeason;
        this.events = events;
    }

    /**
     * Changes the season's rules. What the request leaves out stays as it is.
     *
     * @throws ResponseStatusException 400 when a value is wrong, 409 when the season is finished;
     *     nothing changes then
     */
    public RulesView save(String slug, int number, RulesInput input) {
        Season season = requireOpen(slug, number);
        change(season, input);
        reaward(season);
        rulesChanged(season);
        return seasons.rules(slug, number);
    }

    /**
     * Keeps the season's rules, with these changes, for the organisation's next season; the season
     * itself doesn't change, so a finished one can be the starting point too.
     *
     * @throws ResponseStatusException 400 when a value is wrong
     */
    public RulesView saveForNextSeason(String slug, int number, RulesInput input) {
        Season season = seasons.require(slug, number);
        Season draft = new Season(
                season.getOrganizationId(),
                season.getNumber(),
                season.getName(),
                season.getMatchCount(),
                season.getGames(),
                season.getPointsPerWin());
        draft.useRules(RulesSnapshot.of(season));
        change(draft, input);
        RulesSnapshot rules = RulesSnapshot.of(draft);
        nextSeason.save(season.getOrganizationId(), rules);
        rulesChanged(season);
        return SeasonService.rulesOf(rules);
    }

    /** Applies the changes to the season; what the input leaves out stays as it is. */
    /** Tells the other admins the scoring rules changed. */
    private void rulesChanged(Season season) {
        events.publishEvent(
                new AdminChanged(season.getOrganizationId(), "RULES_CHANGED", Map.of("season", season.getName())));
    }

    private static void change(Season season, RulesInput input) {
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
        rulesChanged(season);
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
            threshold = checkThreshold(bonus.getCode(), change.threshold());
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

    /**
     * Adds a bonus of the admins' own at the end of the season's list, under a code made from its
     * name, and works out who won it in every saved game.
     *
     * @throws ResponseStatusException 400 when a value is wrong, 409 when the season is finished
     */
    public RulesView addBonus(String slug, int number, NewBonusInput input) {
        Season season = requireOpen(slug, number);
        String label = input.label() == null ? "" : input.label().strip();
        if (label.isEmpty() || label.length() > MAX_LABEL) {
            throw badRequest("A bonus's name is 1 to " + MAX_LABEL + " characters");
        }
        BonusAward award = input.award();
        if (award == null || !CUSTOM_AWARDS.contains(award)) {
            throw badRequest("A bonus goes to the highest or lowest value of a stat, to whoever reaches a value,"
                    + " or to whoever an admin ticks");
        }
        Stat stat = award == BonusAward.MANUAL ? null : input.stat();
        StatRule rule = stat == null ? null : season.getStatRules().get(stat);
        if (award != BonusAward.MANUAL && (rule == null || !rule.recorded())) {
            throw badRequest("Choose a stat the season records");
        }
        if (award == BonusAward.MANUAL && input.stat() != null) {
            throw badRequest("A ticked bonus compares no stat");
        }
        int points = checkPoints("A bonus's points", BigDecimal.valueOf(input.points()), BigDecimal.ZERO)
                .intValueExact();
        BigDecimal threshold = null;
        if (award == BonusAward.AT_LEAST) {
            threshold = checkThreshold(label, input.threshold());
        } else if (input.threshold() != null) {
            throw badRequest("Only an \"at least\" bonus has a threshold");
        }
        season.addBonus(codeFor(season, label), label, award, stat, points, threshold);
        reaward(season);
        rulesChanged(season);
        return seasons.rules(slug, number);
    }

    /**
     * Removes a bonus, and with it every award of it in the season's games.
     *
     * @throws ResponseStatusException 404 for a code the season doesn't have, 409 when the season
     *     is finished
     */
    public RulesView removeBonus(String slug, int number, String code) {
        Season season = requireOpen(slug, number);
        SeasonBonus bonus = season.bonus(code)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, season.getName() + " has no bonus " + code));
        lines.findBySeason(season).forEach(line -> line.withdraw(bonus));
        lines.flush();
        season.removeBonus(bonus);
        reaward(season);
        rulesChanged(season);
        return seasons.rules(slug, number);
    }

    /**
     * A short code from a bonus's name, as the tables show it: the first letters of its words, such
     * as CK for "Clutch king", numbered when the season already has it.
     */
    static String codeFor(Season season, String label) {
        StringBuilder initials = new StringBuilder();
        for (String word : label.strip().split("\\s+")) {
            String plain = Normalizer.normalize(word, Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9]", "");
            if (!plain.isEmpty() && initials.length() < MAX_CODE) {
                initials.append(Character.toUpperCase(plain.charAt(0)));
            }
        }
        String base = initials.isEmpty() ? "B" : initials.toString();
        String code = base;
        for (int n = 2; season.bonus(code).isPresent(); n++) {
            code = base + n;
        }
        return code;
    }

    private static BigDecimal checkThreshold(String what, BigDecimal threshold) {
        if (threshold == null
                || threshold.stripTrailingZeros().scale() > DECIMALS
                || threshold.signum() < 0
                || threshold.compareTo(MAX_THRESHOLD) > 0) {
            throw badRequest(what + "'s threshold must be between 0 and " + MAX_THRESHOLD);
        }
        return threshold;
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
    /**
     * A bonus of the admins' own.
     *
     * @param award HIGHEST, LOWEST, AT_LEAST or MANUAL
     * @param stat the stat compared; nothing for a MANUAL bonus
     * @param threshold for an AT_LEAST bonus, the value to reach; nothing otherwise
     */
    public record NewBonusInput(String label, BonusAward award, Stat stat, int points, BigDecimal threshold) {}

    public record RulesInput(
            List<StatRuleInput> stats, Integer pointsPerWin, BigDecimal kdaPoints, List<BonusInput> bonuses) {}
}
