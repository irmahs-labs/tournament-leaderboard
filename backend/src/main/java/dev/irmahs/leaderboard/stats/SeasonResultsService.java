package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.matches.Match;
import dev.irmahs.leaderboard.matches.MatchRepository;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.players.PlayerAlias;
import dev.irmahs.leaderboard.players.PlayerRepository;
import dev.irmahs.leaderboard.ranks.Rank;
import dev.irmahs.leaderboard.scoring.ScoringService;
import dev.irmahs.leaderboard.seasons.BonusView;
import dev.irmahs.leaderboard.seasons.Points;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.SeasonService;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A season's results for the public pages: the leaderboard, its summary tiles, the matches grid
 * and a match's detail. Points are never stored: they're computed here on every read, from the
 * season's rules, by {@link ScoringService}.
 */
@Service
@Transactional(readOnly = true)
public class SeasonResultsService {

    private static final Comparator<Standing> BEST_FIRST = Comparator.comparing(Standing::points)
            .reversed()
            .thenComparing(Comparator.comparingInt(Standing::wins).reversed())
            .thenComparing(Standing::riotId, String.CASE_INSENSITIVE_ORDER);

    private final SeasonService seasons;
    private final MatchRepository matches;
    private final GameStatRepository lines;
    private final ScoringService scoring;
    private final PlayerRepository players;

    SeasonResultsService(
            SeasonService seasons,
            MatchRepository matches,
            GameStatRepository lines,
            ScoringService scoring,
            PlayerRepository players) {
        this.seasons = seasons;
        this.matches = matches;
        this.lines = lines;
        this.scoring = scoring;
        this.players = players;
    }

    public LeaderboardView leaderboard(String slug, int seasonNumber) {
        Season season = seasons.require(slug, seasonNumber);
        List<GameStat> seasonLines = lines.findBySeason(season);
        return new LeaderboardView(
                SeasonRef.of(season), columns(season, seasonLines), bonuses(season), standings(season, seasonLines));
    }

    public SummaryView summary(String slug, int seasonNumber) {
        Season season = seasons.require(slug, seasonNumber);
        List<GameStat> seasonLines = lines.findBySeason(season);
        LocalDate today = LocalDate.now();
        NextMatch next = matches.findBySeasonOrderByMatchNumber(season).stream()
                .filter(match ->
                        match.getMatchDate() != null && !match.getMatchDate().isBefore(today))
                .min(Comparator.comparing(Match::getMatchDate))
                .map(match -> new NextMatch(match.getMatchNumber(), match.getMatchDate()))
                .orElse(null);
        return new SummaryView(
                season.getMatchCount(),
                distinct(seasonLines, line -> line.getGame().getMatch().getId()),
                distinct(seasonLines, line -> line.getGame().getId()),
                distinct(seasonLines, line -> line.getPlayer().getId()),
                next);
    }

    /** Every match of the season, played or only planned, in order. */
    public List<MatchCard> matches(String slug, int seasonNumber) {
        Season season = seasons.require(slug, seasonNumber);
        Map<Integer, List<GameStat>> byMatch = lines.findBySeason(season).stream()
                .collect(Collectors.groupingBy(line -> line.getGame().getMatch().getId()));
        return matches.findBySeasonOrderByMatchNumber(season).stream()
                .map(match -> {
                    List<GameStat> matchLines = byMatch.getOrDefault(match.getId(), List.of());
                    TopPlayer top = standings(season, matchLines).stream()
                            .findFirst()
                            .map(best -> new TopPlayer(best.playerId(), best.riotId(), best.points()))
                            .orElse(null);
                    return new MatchCard(
                            match.getMatchNumber(),
                            match.getMatchDate(),
                            distinct(matchLines, line -> line.getGame().getId()),
                            distinct(matchLines, line -> line.getPlayer().getId()),
                            top);
                })
                .toList();
    }

    /**
     * One match: each game's lines, and the players' totals over the match.
     *
     * @throws ResponseStatusException 404 when the season has no such match
     */
    public MatchView match(String slug, int seasonNumber, int matchNumber) {
        Season season = seasons.require(slug, seasonNumber);
        Match match = matches.findBySeasonOrderByMatchNumber(season).stream()
                .filter(candidate -> candidate.getMatchNumber() == matchNumber)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, season.getName() + " has no match " + matchNumber));
        List<GameStat> matchLines = lines.findBySeason(season).stream()
                .filter(line -> line.getGame().getMatch().getId().equals(match.getId()))
                .toList();

        Map<Integer, List<GameLine>> byGame = new TreeMap<>();
        for (GameStat line : matchLines) {
            byGame.computeIfAbsent(line.getGame().getGameNumber(), game -> new ArrayList<>())
                    .add(gameLine(season, line));
        }
        List<GameView> games = byGame.entrySet().stream()
                .map(game -> new GameView(
                        game.getKey(),
                        game.getValue().stream()
                                .sorted(Comparator.comparing(GameLine::points)
                                        .reversed()
                                        .thenComparing(GameLine::riotId, String.CASE_INSENSITIVE_ORDER))
                                .toList()))
                .toList();
        return new MatchView(
                SeasonRef.of(season),
                match.getMatchNumber(),
                match.getMatchDate(),
                columns(season, matchLines),
                bonuses(season),
                games,
                standings(season, matchLines));
    }

    /**
     * One player's season: who they are, their standing and the player just ahead, and each match
     * they played with their place in it.
     *
     * @throws ResponseStatusException 404 when the organisation has no such player
     */
    public PlayerSeasonView player(String slug, int seasonNumber, UUID playerId) {
        Season season = seasons.require(slug, seasonNumber);
        Player player = players.findByIdAndOrganizationId(playerId, season.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no such player"));
        List<GameStat> seasonLines = lines.findBySeason(season);
        List<Standing> all = standings(season, seasonLines);
        Standing mine = mineIn(all, playerId);
        // Best first, so the closest one above is the last with more points.
        Standing ahead = mine == null
                ? null
                : all.stream()
                        .filter(other -> other.points().compareTo(mine.points()) > 0)
                        .reduce((first, second) -> second)
                        .orElse(null);

        Map<Integer, List<GameStat>> byMatch = seasonLines.stream()
                .collect(Collectors.groupingBy(line -> line.getGame().getMatch().getId()));
        List<PlayerMatch> played = new ArrayList<>();
        for (Match match : matches.findBySeasonOrderByMatchNumber(season)) {
            List<GameStat> matchLines = byMatch.getOrDefault(match.getId(), List.of());
            Standing inMatch = mineIn(standings(season, matchLines), playerId);
            if (inMatch != null) {
                played.add(new PlayerMatch(match.getMatchNumber(), match.getMatchDate(), inMatch));
            }
        }
        List<GameStat> myLines = seasonLines.stream()
                .filter(line -> line.getPlayer().getId().equals(playerId))
                .toList();
        return new PlayerSeasonView(
                SeasonRef.of(season),
                new PlayerRef(
                        player.getId(),
                        player.getRiotId(),
                        player.getAliases().stream().map(PlayerAlias::getAlias).toList(),
                        RankRef.of(player.getCurrentRank())),
                mine,
                all.size(),
                ahead == null ? null : new Ahead(ahead.riotId(), ahead.points().subtract(mine.points())),
                columns(season, myLines),
                played);
    }

    private static Standing mineIn(List<Standing> standings, UUID playerId) {
        return standings.stream()
                .filter(standing -> standing.playerId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    private GameLine gameLine(Season season, GameStat line) {
        return GameLine.of(scoring, season, line);
    }

    /** The players of these lines, best first. Players level on points share a rank. */
    private List<Standing> standings(Season season, Collection<GameStat> gameLines) {
        Map<UUID, PlayerTally> tallies = new LinkedHashMap<>();
        for (GameStat line : gameLines) {
            Player player = line.getPlayer();
            tallies.computeIfAbsent(player.getId(), id -> new PlayerTally(player))
                    .add(line, scoring.gamePoints(season, line.getLine(), line.getBonuses(), line.isWin()));
        }
        List<Standing> unranked = tallies.values().stream()
                .map(tally -> new Standing(
                        0,
                        tally.player().getId(),
                        tally.player().getRiotId(),
                        RankRef.of(tally.player().getCurrentRank()),
                        tally.games(),
                        tally.wins(),
                        Points.plain(tally.points()),
                        tally.totals(),
                        tally.averages(),
                        tally.kda(),
                        tally.bonusCounts()))
                .sorted(BEST_FIRST)
                .toList();
        return unranked.stream()
                .map(standing -> standing.ranked(1
                        + (int) unranked.stream()
                                .filter(other -> other.points().compareTo(standing.points()) > 0)
                                .count()))
                .toList();
    }

    /**
     * The stats to show as columns: those the season records, with a value in at least one of these
     * lines. A stat switched off disappears from the season's tables, as it no longer counts.
     */
    private static Set<Stat> columns(Season season, Collection<GameStat> gameLines) {
        Set<Stat> columns = EnumSet.noneOf(Stat.class);
        for (GameStat line : gameLines) {
            for (Stat stat : Stat.values()) {
                StatRule rule = season.getStatRules().get(stat);
                if (rule != null && rule.recorded() && line.getLine().value(stat) != null) {
                    columns.add(stat);
                }
            }
        }
        return columns;
    }

    private static List<BonusView> bonuses(Season season) {
        return season.getBonuses().stream().map(BonusView::of).toList();
    }

    private static <K> int distinct(Collection<GameStat> gameLines, java.util.function.Function<GameStat, K> key) {
        return (int) gameLines.stream().map(key).distinct().count();
    }

    public record SeasonRef(int number, String name) {

        static SeasonRef of(Season season) {
            return new SeasonRef(season.getNumber(), season.getName());
        }
    }

    /**
     * One player's row in a leaderboard or a match's combined table.
     *
     * @param currentRank the player's Valorant rank today; nothing when nobody set it
     * @param totals the countable stats added up
     * @param averages each stat per game that recorded it
     * @param kda (kills + assists) / deaths, counting no deaths as one
     * @param bonuses how many times the player won each bonus, by its code
     */
    public record Standing(
            int rank,
            UUID playerId,
            String riotId,
            RankRef currentRank,
            int games,
            int wins,
            BigDecimal points,
            Map<Stat, Long> totals,
            Map<Stat, BigDecimal> averages,
            BigDecimal kda,
            Map<String, Integer> bonuses) {

        Standing ranked(int newRank) {
            return new Standing(
                    newRank, playerId, riotId, currentRank, games, wins, points, totals, averages, kda, bonuses);
        }
    }

    /**
     * A rank as the public pages show it.
     *
     * @param color the rank's colour, as #RRGGBB; nothing when it has none
     */
    public record RankRef(String name, String color) {

        static RankRef of(Rank rank) {
            return rank == null ? null : new RankRef(rank.getRankName(), rank.getColor());
        }
    }

    /**
     * @param columns the stats with a value in at least one game, in display order
     */
    public record LeaderboardView(
            SeasonRef season, Set<Stat> columns, List<BonusView> bonuses, List<Standing> standings) {}

    public record NextMatch(int matchNumber, LocalDate matchDate) {}

    /**
     * @param matchCount how many matches the season plans
     * @param nextMatch the next match dated today or later; nothing when none is planned
     */
    public record SummaryView(int matchCount, int matchesPlayed, int gamesPlayed, int players, NextMatch nextMatch) {}

    public record TopPlayer(UUID playerId, String riotId, BigDecimal points) {}

    /**
     * @param games how many games of the match have scores
     * @param topPlayer the player with the most points over the match; nothing before it's played
     */
    public record MatchCard(int matchNumber, LocalDate matchDate, int games, int players, TopPlayer topPlayer) {}

    /**
     * @param stats the stats the game recorded for the player
     * @param bonuses the codes of the bonuses the player won in the game
     */
    public record GameLine(
            UUID playerId,
            String riotId,
            Map<Stat, BigDecimal> stats,
            boolean win,
            List<String> bonuses,
            BigDecimal points) {

        /** A saved line, with its points under the season's rules. */
        public static GameLine of(ScoringService scoring, Season season, GameStat line) {
            Map<Stat, BigDecimal> stats = new EnumMap<>(Stat.class);
            for (Stat stat : Stat.values()) {
                BigDecimal value = line.getLine().value(stat);
                if (value != null) {
                    stats.put(stat, value);
                }
            }
            return new GameLine(
                    line.getPlayer().getId(),
                    line.getPlayer().getRiotId(),
                    stats,
                    line.isWin(),
                    line.getBonuses().stream()
                            .map(SeasonBonus::getCode)
                            .sorted()
                            .toList(),
                    Points.plain(scoring.gamePoints(season, line.getLine(), line.getBonuses(), line.isWin())));
        }
    }

    public record GameView(int gameNumber, List<GameLine> lines) {}

    /**
     * A player as their profile shows them.
     *
     * @param aliases their other names: previous Riot IDs and nicknames
     */
    /** A player as the public pages show them: never their Discord, which only admins see. */
    public record PlayerRef(UUID id, String riotId, List<String> aliases, RankRef currentRank) {}

    /** The player just ahead, and how many points they lead by. */
    public record Ahead(String riotId, BigDecimal points) {}

    /**
     * @param standing the player's totals over the match; its rank is their place in it
     */
    public record PlayerMatch(int matchNumber, LocalDate matchDate, Standing standing) {}

    /**
     * @param standing nothing when they haven't played this season
     * @param players how many players have played this season
     * @param ahead nothing when they lead, or haven't played
     * @param columns the stats recorded in their games
     */
    public record PlayerSeasonView(
            SeasonRef season,
            PlayerRef player,
            Standing standing,
            int players,
            Ahead ahead,
            Set<Stat> columns,
            List<PlayerMatch> matches) {}

    /**
     * @param combined the players' totals over the whole match
     */
    public record MatchView(
            SeasonRef season,
            int matchNumber,
            LocalDate matchDate,
            Set<Stat> columns,
            List<BonusView> bonuses,
            List<GameView> games,
            List<Standing> combined) {}
}
