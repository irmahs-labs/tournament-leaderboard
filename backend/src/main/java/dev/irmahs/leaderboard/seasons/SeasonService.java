package dev.irmahs.leaderboard.seasons;

import dev.irmahs.leaderboard.organizations.OrganizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reads an organisation's seasons and their rules. */
@Service
@Transactional(readOnly = true)
public class SeasonService {

    private final OrganizationService organizations;
    private final SeasonRepository seasons;

    SeasonService(OrganizationService organizations, SeasonRepository seasons) {
        this.organizations = organizations;
        this.seasons = seasons;
    }

    /**
     * One season of an organisation, by the number in the address.
     *
     * @throws ResponseStatusException 404 when the organisation or the season doesn't exist
     */
    public Season require(String slug, int number) {
        UUID organizationId = organizations.requireActive(slug).getId();
        return seasons.findByOrganizationIdAndNumber(organizationId, number)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, slug + " has no season " + number));
    }

    /** The organisation's seasons, oldest first. */
    public List<SeasonView> list(String slug) {
        return seasons
                .findByOrganizationIdOrderByNumber(
                        organizations.requireActive(slug).getId())
                .stream()
                .map(SeasonView::of)
                .toList();
    }

    /** How the season counts points: each stat's rule, the bonuses, and the points for a win. */
    public RulesView rules(String slug, int number) {
        Season season = require(slug, number);
        List<StatRuleView> stats = season.getStatRules().entrySet().stream()
                .map(rule -> new StatRuleView(
                        rule.getKey(),
                        rule.getValue().recorded(),
                        rule.getValue().effect(),
                        rule.getKey().isCountable()))
                .toList();
        List<BonusView> bonuses =
                season.getBonuses().stream().map(BonusView::of).toList();
        return new RulesView(season.getPointsPerWin(), stats, bonuses);
    }

    /**
     * @param finished whether the season is closed
     */
    public record SeasonView(
            int number,
            String name,
            java.time.LocalDate startsOn,
            java.time.LocalDate endsOn,
            int matchCount,
            int minGames,
            int maxGames,
            boolean finished) {

        static SeasonView of(Season season) {
            return new SeasonView(
                    season.getNumber(),
                    season.getName(),
                    season.getStartsOn(),
                    season.getEndsOn(),
                    season.getMatchCount(),
                    season.getGames().min(),
                    season.getGames().max(),
                    season.getFinishedAt() != null);
        }
    }

    /**
     * @param effect 1 adds to the points, -1 subtracts, 0 isn't counted
     * @param countable false for averages and percentages, which can never count
     */
    public record StatRuleView(Stat stat, boolean recorded, int effect, boolean countable) {}

    public record RulesView(int pointsPerWin, List<StatRuleView> stats, List<BonusView> bonuses) {}
}
