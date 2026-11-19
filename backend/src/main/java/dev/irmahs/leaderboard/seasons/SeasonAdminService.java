package dev.irmahs.leaderboard.seasons;

import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import dev.irmahs.leaderboard.seasons.SeasonService.SeasonView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The admin Seasons screen: start a season, and edit one. */
@Service
@Transactional
public class SeasonAdminService {

    static final int MAX_NAME = 40;
    static final int MAX_MATCHES = 20;
    static final int MAX_GAMES = 9;

    private final OrganizationService organizations;
    private final SeasonRepository seasons;
    private final SeasonService reader;

    SeasonAdminService(OrganizationService organizations, SeasonRepository seasons, SeasonService reader) {
        this.organizations = organizations;
        this.seasons = seasons;
        this.reader = reader;
    }

    /**
     * Starts the organisation's next season, numbered after its latest. It takes the rules of the
     * season it copies, or the platform defaults; what the request leaves out comes from the same
     * place.
     *
     * @throws ResponseStatusException 400 when the details are wrong, 404 when the season to copy
     *     doesn't exist
     */
    public SeasonView start(String slug, NewSeason input) {
        UUID organizationId = organizations.requireActive(slug).getId();
        Season source = input.copyRulesFrom() == null ? null : reader.require(slug, input.copyRulesFrom());
        int number = seasons.findFirstByOrganizationIdOrderByNumberDesc(organizationId)
                        .map(Season::getNumber)
                        .orElse(0)
                + 1;
        String name = input.name() == null || input.name().isBlank() ? "Season " + number : checkName(input.name());
        int matchCount =
                orElse(input.matchCount(), source == null ? PlatformDefaults.MATCH_COUNT : source.getMatchCount());
        GameRange defaults = source == null ? PlatformDefaults.GAMES : source.getGames();
        GameRange games =
                new GameRange(orElse(input.minGames(), defaults.min()), orElse(input.maxGames(), defaults.max()));
        checkShape(matchCount, games, input.startsOn(), input.endsOn());
        int pointsPerWin = source == null ? PlatformDefaults.POINTS_PER_WIN : source.getPointsPerWin();

        Season season = new Season(organizationId, number, name, matchCount, games, pointsPerWin);
        season.setDates(input.startsOn(), input.endsOn());
        if (source == null) {
            PlatformDefaults.apply(season);
        } else {
            season.copyRulesFrom(source);
        }
        return SeasonView.of(seasons.save(season));
    }

    /**
     * Changes a season's name, dates and shape, and closes or reopens it. A season can't lose a
     * match or a game number that already has a game.
     *
     * @throws ResponseStatusException 400 when the details are wrong, 409 when saved games don't fit
     */
    public SeasonView edit(String slug, int number, SeasonInput input) {
        Season season = reader.require(slug, number);
        String name = checkName(input.name());
        int matchCount = required(input.matchCount(), "matchCount");
        GameRange games = new GameRange(required(input.minGames(), "minGames"), required(input.maxGames(), "maxGames"));
        checkShape(matchCount, games, input.startsOn(), input.endsOn());
        Integer highestMatch = seasons.highestMatchNumber(season);
        if (highestMatch != null && highestMatch > matchCount) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, season.getName() + " already has match " + highestMatch);
        }
        Integer highestGame = seasons.highestGameNumber(season);
        if (highestGame != null && highestGame > games.max()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, season.getName() + " already has a match with game " + highestGame);
        }
        season.edit(name, input.startsOn(), input.endsOn(), matchCount, games);
        if (Boolean.TRUE.equals(input.finished())) {
            season.finish(Instant.now());
        } else if (Boolean.FALSE.equals(input.finished())) {
            season.reopen();
        }
        return SeasonView.of(season);
    }

    private static String checkName(String value) {
        if (value == null || value.isBlank()) {
            throw badRequest("A season needs a name");
        }
        String name = value.strip();
        if (name.length() > MAX_NAME) {
            throw badRequest("A season's name has at most " + MAX_NAME + " characters");
        }
        return name;
    }

    private static void checkShape(int matchCount, GameRange games, LocalDate startsOn, LocalDate endsOn) {
        if (matchCount < 1 || matchCount > MAX_MATCHES) {
            throw badRequest("A season has 1 to " + MAX_MATCHES + " matches");
        }
        if (games.min() < 1 || games.max() > MAX_GAMES || games.min() > games.max()) {
            throw badRequest("A match has 1 to " + MAX_GAMES + " games, the fewest no more than the most");
        }
        if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
            throw badRequest("A season can't end before it starts");
        }
    }

    private static int orElse(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static int required(Integer value, String field) {
        if (value == null) {
            throw badRequest(field + " is required");
        }
        return value;
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * @param name "Season N" when empty
     * @param copyRulesFrom the number of the season whose rules to take; the platform defaults when
     *     empty
     */
    public record NewSeason(
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            Integer matchCount,
            Integer minGames,
            Integer maxGames,
            Integer copyRulesFrom) {}

    /**
     * @param finished true closes the season, false reopens it, empty leaves it as it is
     */
    public record SeasonInput(
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            Integer matchCount,
            Integer minGames,
            Integer maxGames,
            Boolean finished) {}
}
