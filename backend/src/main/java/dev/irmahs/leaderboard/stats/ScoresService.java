package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.matches.Game;
import dev.irmahs.leaderboard.matches.GameRepository;
import dev.irmahs.leaderboard.matches.Match;
import dev.irmahs.leaderboard.matches.MatchRepository;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.players.PlayerRepository;
import dev.irmahs.leaderboard.scoring.BonusCalculator;
import dev.irmahs.leaderboard.scoring.BonusCalculator.Entry;
import dev.irmahs.leaderboard.scoring.ScoringService;
import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.SeasonService;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import dev.irmahs.leaderboard.stats.SeasonResultsService.GameLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Enter scores: load, save and clear one game, check its bonuses, and carry the previous game's
 * players over. A save is all or nothing.
 */
@Service
public class ScoresService {

    private static final BigDecimal MAX_WHOLE = BigDecimal.valueOf(Short.MAX_VALUE);
    private static final BigDecimal MAX_AVERAGE = new BigDecimal("9999.9");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** The most players a game can have, as players_per_match allows. */
    private static final BigDecimal MAX_PLAYERS = BigDecimal.valueOf(40);

    private final SeasonService seasons;
    private final OrganizationService organizations;
    private final MatchRepository matches;
    private final GameRepository games;
    private final GameStatRepository lines;
    private final PlayerRepository players;
    private final BonusCalculator bonuses;
    private final ScoringService scoring;

    ScoresService(
            SeasonService seasons,
            OrganizationService organizations,
            MatchRepository matches,
            GameRepository games,
            GameStatRepository lines,
            PlayerRepository players,
            BonusCalculator bonuses,
            ScoringService scoring) {
        this.seasons = seasons;
        this.organizations = organizations;
        this.matches = matches;
        this.games = games;
        this.lines = lines;
        this.players = players;
        this.bonuses = bonuses;
        this.scoring = scoring;
    }

    /** The game's lines, or none when it hasn't been saved. */
    @Transactional(readOnly = true)
    public GameScores load(String slug, int seasonNumber, int matchNumber, int gameNumber) {
        Season season = seasons.require(slug, seasonNumber);
        checkNumbers(season, matchNumber, gameNumber);
        Optional<Match> match = matches.findBySeasonAndMatchNumber(season, matchNumber);
        List<GameStat> saved = match.flatMap(found -> games.findByMatchAndGameNumber(found, gameNumber))
                .map(lines::findByGame)
                .orElse(List.of());
        return view(
                season, matchNumber, gameNumber, match.map(Match::getMatchDate).orElse(null), saved);
    }

    /**
     * Replaces the game's lines. Creates the match, the game and any new player as needed, works
     * out the automatic bonuses, and takes only the manual ones from the request.
     *
     * @throws ResponseStatusException 400 when anything in the request is wrong; nothing is saved
     */
    @Transactional
    public GameScores save(String slug, int seasonNumber, int matchNumber, int gameNumber, GameScoresInput input) {
        Season season = seasons.require(slug, seasonNumber);
        checkNumbers(season, matchNumber, gameNumber);
        List<LineInput> requested = input.lines() == null ? List.of() : input.lines();
        if (requested.isEmpty()) {
            throw badRequest("A game needs at least one player; clear it to remove it");
        }
        Map<String, SeasonBonus> manual = season.getBonuses().stream()
                .filter(bonus -> bonus.getAward() == BonusAward.MANUAL)
                .collect(Collectors.toMap(bonus -> bonus.getCode().toUpperCase(Locale.ROOT), Function.identity()));
        List<StatLine> statLines = new ArrayList<>();
        for (LineInput line : requested) {
            statLines.add(statLine(season, line.stats()));
            for (String code : line.manualCodes()) {
                if (!manual.containsKey(code)) {
                    throw badRequest(season.getName() + " has no manual bonus " + code);
                }
            }
        }
        // Players last: everything else is checked before a new player is added.
        List<Player> roster = resolvePlayers(season.getOrganizationId(), requested);

        Match match = matches.findBySeasonAndMatchNumber(season, matchNumber)
                .orElseGet(() -> matches.save(new Match(season, matchNumber, input.matchDate())));
        if (input.matchDate() != null) {
            match.reschedule(input.matchDate());
        }
        Game game = games.findByMatchAndGameNumber(match, gameNumber)
                .orElseGet(() -> games.save(new Game(match, gameNumber)));
        lines.deleteAll(lines.findByGame(game));
        lines.flush();

        Map<Integer, Entry> byIndex = new LinkedHashMap<>();
        for (int i = 0; i < statLines.size(); i++) {
            byIndex.put(i, new Entry(statLines.get(i), requested.get(i).won()));
        }
        Map<Integer, Set<SeasonBonus>> automatic = bonuses.award(season, byIndex);
        List<GameStat> saved = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            LineInput line = requested.get(i);
            GameStat stat = new GameStat(game, roster.get(i), statLines.get(i), line.won());
            automatic.get(i).forEach(stat::award);
            line.manualCodes().forEach(code -> stat.award(manual.get(code)));
            saved.add(lines.save(stat));
        }
        organizations.recordActivity(season.getOrganizationId());
        return view(season, matchNumber, gameNumber, match.getMatchDate(), saved);
    }

    /** Removes the game and its lines. The match stays, with its date. */
    @Transactional
    public void clear(String slug, int seasonNumber, int matchNumber, int gameNumber) {
        Season season = seasons.require(slug, seasonNumber);
        checkNumbers(season, matchNumber, gameNumber);
        matches.findBySeasonAndMatchNumber(season, matchNumber)
                .flatMap(match -> games.findByMatchAndGameNumber(match, gameNumber))
                .ifPresent(game -> {
                    lines.deleteAll(lines.findByGame(game));
                    games.delete(game);
                    organizations.recordActivity(season.getOrganizationId());
                });
    }

    /** The automatic bonuses these lines would win, in the same order, without saving anything. */
    @Transactional(readOnly = true)
    public List<List<String>> checkBonuses(String slug, int seasonNumber, List<CheckedLine> checked) {
        Season season = seasons.require(slug, seasonNumber);
        Map<Integer, Entry> byIndex = new LinkedHashMap<>();
        for (int i = 0; i < checked.size(); i++) {
            CheckedLine line = checked.get(i);
            byIndex.put(i, new Entry(statLine(season, line.stats()), line.won()));
        }
        Map<Integer, Set<SeasonBonus>> awarded = bonuses.award(season, byIndex);
        List<List<String>> codes = new ArrayList<>();
        for (int i = 0; i < checked.size(); i++) {
            codes.add(awarded.get(i).stream()
                    .sorted(Comparator.comparingInt(SeasonBonus::getPosition))
                    .map(SeasonBonus::getCode)
                    .toList());
        }
        return codes;
    }

    /** The players of the season's latest saved game before this one: "carried over from…". */
    @Transactional(readOnly = true)
    public PreviousRoster previousRoster(String slug, int seasonNumber, int matchNumber, int gameNumber) {
        Season season = seasons.require(slug, seasonNumber);
        checkNumbers(season, matchNumber, gameNumber);
        return games.findSavedBefore(season, matchNumber, gameNumber).stream()
                .findFirst()
                .map(game -> new PreviousRoster(
                        game.getMatch().getMatchNumber(),
                        game.getGameNumber(),
                        lines.findByGame(game).stream()
                                .map(line -> new RosterPlayer(
                                        line.getPlayer().getId(),
                                        line.getPlayer().getRiotId()))
                                .sorted(Comparator.comparing(RosterPlayer::riotId, String.CASE_INSENSITIVE_ORDER))
                                .toList()))
                .orElse(new PreviousRoster(null, null, List.of()));
    }

    private static void checkNumbers(Season season, int matchNumber, int gameNumber) {
        if (matchNumber < 1 || matchNumber > season.getMatchCount()) {
            throw badRequest(season.getName() + " has matches 1 to " + season.getMatchCount());
        }
        if (gameNumber < 1 || gameNumber > season.getGames().max()) {
            throw badRequest(
                    season.getName() + " has up to " + season.getGames().max() + " games a match");
        }
    }

    /**
     * Finds each line's player, and only once every line is known to be right, adds the new ones
     * from their Riot ID. Refuses a player twice.
     */
    private List<Player> resolvePlayers(UUID organizationId, List<LineInput> requested) {
        List<Optional<Player>> found = new ArrayList<>();
        List<String> riotIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (LineInput line : requested) {
            Optional<Player> player;
            String riotId = line.riotId() == null ? "" : line.riotId().strip();
            if (line.playerId() != null) {
                player = Optional.of(players.findByIdAndOrganizationId(line.playerId(), organizationId)
                        .orElseThrow(() -> badRequest("There's no player " + line.playerId() + " here")));
                riotId = player.get().getRiotId();
            } else {
                if (riotId.isEmpty() || riotId.length() > 100) {
                    throw badRequest("Each line needs a player: a playerId, or a Riot ID of 1 to 100 characters");
                }
                player = players.findByOrganizationIdAndRiotIdIgnoreCase(organizationId, riotId);
            }
            if (!seen.add(riotId.toLowerCase(Locale.ROOT))) {
                throw badRequest(riotId + " is in the game twice");
            }
            found.add(player);
            riotIds.add(riotId);
        }
        List<Player> roster = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            String riotId = riotIds.get(i);
            roster.add(found.get(i).orElseGet(() -> players.save(new Player(organizationId, riotId, null, null))));
        }
        return roster;
    }

    /** Checks each stat against the season's rules and its range, and builds the line. */
    private static StatLine statLine(Season season, Map<Stat, BigDecimal> stats) {
        Map<Stat, BigDecimal> values = new EnumMap<>(Stat.class);
        if (stats != null) {
            values.putAll(stats);
            values.values().removeIf(java.util.Objects::isNull);
        }
        for (Map.Entry<Stat, BigDecimal> entry : values.entrySet()) {
            Stat stat = entry.getKey();
            BigDecimal value = entry.getValue();
            StatRule rule = season.getStatRules().get(stat);
            if (rule == null || !rule.recorded()) {
                throw badRequest(season.getName() + " doesn't record " + stat);
            }
            String problem = rangeProblem(stat, value);
            if (problem != null) {
                throw badRequest(stat + " " + problem);
            }
        }
        return StatLine.of(values);
    }

    private static String rangeProblem(Stat stat, BigDecimal value) {
        return switch (stat) {
            case ADR -> decimal(value, 1, BigDecimal.ZERO, MAX_AVERAGE);
            case DAMAGE_DELTA -> decimal(value, 1, MAX_AVERAGE.negate(), MAX_AVERAGE);
            case KAST_PCT, HEADSHOT_PCT -> decimal(value, 2, BigDecimal.ZERO, HUNDRED);
            case PLACE -> decimal(value, 0, BigDecimal.ONE, MAX_PLAYERS);
            case HONOURS -> decimal(value, 0, BigDecimal.ZERO, MAX_PLAYERS);
            default -> decimal(value, 0, BigDecimal.ZERO, MAX_WHOLE);
        };
    }

    private static String decimal(BigDecimal value, int scale, BigDecimal min, BigDecimal max) {
        if (value.stripTrailingZeros().scale() > scale) {
            return scale == 0 ? "must be a whole number" : "has at most " + scale + " decimals";
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            return "must be between " + min.toPlainString() + " and " + max.toPlainString();
        }
        return null;
    }

    private GameScores view(Season season, int matchNumber, int gameNumber, LocalDate matchDate, List<GameStat> saved) {
        List<GameLine> view = saved.stream()
                .map(line -> GameLine.of(scoring, season, line))
                .sorted(Comparator.comparing(GameLine::points)
                        .reversed()
                        .thenComparing(GameLine::riotId, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new GameScores(season.getNumber(), matchNumber, gameNumber, matchDate, !saved.isEmpty(), view);
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * One line of the Enter scores form.
     *
     * @param playerId the player, when they're already on the list
     * @param riotId otherwise, the player's Riot ID: found, or added to the organisation's players
     * @param stats only the stats the season records; any left out stay empty
     * @param win whether the player won the game; nothing means they didn't
     * @param manualBonuses the codes of the manual bonuses the admin ticked
     */
    public record LineInput(
            UUID playerId, String riotId, Map<Stat, BigDecimal> stats, Boolean win, Set<String> manualBonuses) {

        boolean won() {
            return Boolean.TRUE.equals(win);
        }

        /** The ticked manual bonuses' codes in capitals, as the season keys them; none when left out. */
        Set<String> manualCodes() {
            return manualBonuses == null
                    ? Set.of()
                    : manualBonuses.stream()
                            .map(code -> code.toUpperCase(Locale.ROOT))
                            .collect(Collectors.toSet());
        }
    }

    /**
     * One line of the "Check bonuses" button.
     *
     * @param stats the line's stats, as in {@link LineInput}
     * @param win whether the player won the game, for MVP and SVP; nothing means they didn't
     */
    public record CheckedLine(Map<Stat, BigDecimal> stats, Boolean win) {

        boolean won() {
            return Boolean.TRUE.equals(win);
        }
    }

    /**
     * @param matchDate the match's day; nothing keeps the date it has
     */
    public record GameScoresInput(LocalDate matchDate, List<LineInput> lines) {}

    /**
     * @param saved whether the game has been saved
     * @param lines its lines, best first, with their points
     */
    public record GameScores(
            int season, int matchNumber, int gameNumber, LocalDate matchDate, boolean saved, List<GameLine> lines) {}

    public record RosterPlayer(UUID playerId, String riotId) {}

    /**
     * @param matchNumber the match the players come from; nothing when no earlier game is saved
     */
    public record PreviousRoster(Integer matchNumber, Integer gameNumber, List<RosterPlayer> players) {}
}
