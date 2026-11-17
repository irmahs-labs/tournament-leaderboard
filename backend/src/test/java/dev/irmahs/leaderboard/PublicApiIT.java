package dev.irmahs.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.matches.Game;
import dev.irmahs.leaderboard.matches.Match;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import dev.irmahs.leaderboard.stats.GameStat;
import dev.irmahs.leaderboard.stats.StatLine;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The public, read-only API on a real Postgres, with nobody signed in. Two organisations each
 * have a season 3, so every answer also shows that one organisation never sees the other's data.
 *
 * <p>Alsace Arena, season 3, match 1 (points with today's rules: stats, 10 per bonus, 50 per win):
 *
 * <pre>
 * game 1  Kestrel 317 + FB, MP + win = 387     Wren 233 + LD, MA, MD = 263
 * game 2  Kestrel 262 + FB, MP       = 282     Heron 191 + LD, MA + win = 261
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    UUID kestrelId;

    @BeforeAll
    void seed() {
        tx.executeWithoutResult(status -> {
            UUID alsace = save(new Organization("alsace-arena", "Alsace Arena", "Spike Tournament"))
                    .getId();
            Player kestrel = save(new Player(alsace, "Kestrel#EUW", null, null));
            Player wren = save(new Player(alsace, "Wren#EUW", null, null));
            Player heron = save(new Player(alsace, "Heron#EUW", null, null));
            kestrelId = kestrel.getId();

            Season first = save(todaysRules(alsace, 1));
            Game oldGame = save(new Game(save(new Match(first, 1, LocalDate.of(2025, 3, 6))), 1));
            save(new GameStat(oldGame, wren, line(100, 5, 5, 5, 20, 0, 0, 0), false));

            Season third = save(todaysRules(alsace, 3));
            third.setStatRule(Stat.ADR, new StatRule(true, 0));
            Match played = save(new Match(third, 1, LocalDate.of(2026, 10, 2)));
            save(new Match(third, 2, LocalDate.of(2099, 1, 1)));
            Game one = save(new Game(played, 1));
            Game two = save(new Game(played, 2));
            StatLine withAdr =
                    new StatLine(240, null, new BigDecimal("152.3"), null, null, 18, 12, 5, 60, 3, 2, 1, null, null);
            award(save(new GameStat(one, kestrel, withAdr, true)), third, "FB", "MP");
            award(save(new GameStat(one, wren, line(180, 12, 9, 8, 40, 1, 0, 1), false)), third, "LD", "MA", "MD");
            award(save(new GameStat(two, kestrel, line(200, 15, 14, 3, 55, 2, 1, 0), false)), third, "FB", "MP");
            award(save(new GameStat(two, heron, line(150, 9, 10, 6, 35, 1, 0, 0), true)), third, "LD", "MA");

            UUID lyon = save(new Organization("lyon-game-bar", "Lyon Game Bar", "Friday Clash"))
                    .getId();
            Player osprey = save(new Player(lyon, "Osprey#EUW", null, null));
            Season lyonThird = save(todaysRules(lyon, 3));
            Game lyonGame = save(new Game(save(new Match(lyonThird, 1, LocalDate.of(2026, 10, 9))), 1));
            save(new GameStat(lyonGame, osprey, line(999, 40, 1, 20, 99, 9, 9, 9), true));

            save(new Organization("closed-bar", "Closed Bar", null));
            em.flush();
            em.createNativeQuery("UPDATE organizations SET status = 'SUSPENDED' WHERE slug = 'closed-bar'")
                    .executeUpdate();
        });
    }

    @Test
    void describesAnOrganisation() {
        assertThat(get("/api/orgs/alsace-arena")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"slug": "alsace-arena", "name": "Alsace Arena", "tournamentName": "Spike Tournament",
                         "defaultLocale": "en", "accentColor": null, "playersPerMatch": 10}""");
    }

    @Test
    void listsAnOrganisationsOwnSeasons() {
        assertThat(get("/api/orgs/alsace-arena/seasons"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"number": 1, "name": "Season 1", "matchCount": 8, "minGames": 1, "maxGames": 5, "finished": false},
                         {"number": 3, "name": "Season 3"}]""");
        assertThat(get("/api/orgs/lyon-game-bar/seasons"))
                .bodyJson()
                .extractingPath("$.length()")
                .isEqualTo(1);
    }

    @Test
    void explainsHowASeasonCountsPoints() {
        MvcTestResult rules = get("/api/orgs/alsace-arena/seasons/3/rules");

        assertThat(rules)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.pointsPerWin")
                .isEqualTo(50);
        assertThat(rules)
                .bodyJson()
                .extractingPath("$.stats[?(@.stat == 'DEATHS')]")
                .isEqualTo(List.of(Map.of("stat", "DEATHS", "recorded", true, "effect", -1, "countable", true)));
        assertThat(rules)
                .bodyJson()
                .extractingPath("$.stats[?(@.stat == 'ADR')]")
                .isEqualTo(List.of(Map.of("stat", "ADR", "recorded", true, "effect", 0, "countable", false)));
        assertThat(rules)
                .bodyJson()
                .extractingPath("$.bonuses[*].code")
                .isEqualTo(List.of("FB", "LD", "MA", "MP", "MD"));
        assertThat(rules)
                .bodyJson()
                .extractingPath("$.bonuses[1]")
                .isEqualTo(Map.of(
                        "code", "LD", "label", "Fewest deaths", "award", "LOWEST", "stat", "DEATHS", "points", 10));
    }

    @Test
    void ranksTheSeasonsPlayersWithTheirPoints() {
        MvcTestResult leaderboard = get("/api/orgs/alsace-arena/seasons/3/leaderboard");

        assertThat(leaderboard).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"season": {"number": 3, "name": "Season 3"},
                 "columns": ["PERFORMANCE_SCORE", "ADR", "KILLS", "DEATHS", "ASSISTS", "ECON_RATING",
                             "FIRST_BLOODS", "PLANTS", "DEFUSES"],
                 "standings": [
                   {"rank": 1, "riotId": "Kestrel#EUW", "games": 2, "wins": 1, "points": 669,
                    "totals": {"PERFORMANCE_SCORE": 440, "KILLS": 33, "DEATHS": 26, "ASSISTS": 8,
                               "ECON_RATING": 115, "FIRST_BLOODS": 5, "PLANTS": 3, "DEFUSES": 1},
                    "kda": 1.58, "bonuses": {"FB": 2, "MP": 2}},
                   {"rank": 2, "riotId": "Wren#EUW", "games": 1, "wins": 0, "points": 263,
                    "bonuses": {"LD": 1, "MA": 1, "MD": 1}},
                   {"rank": 3, "riotId": "Heron#EUW", "games": 1, "wins": 1, "points": 261}
                 ]}""");
        // An average is per game that recorded the stat: ADR was recorded in one of Kestrel's two games.
        assertThat(leaderboard)
                .bodyJson()
                .extractingPath("$.standings[0].averages.ADR")
                .isEqualTo(152.3);
        assertThat(leaderboard)
                .bodyJson()
                .extractingPath("$.standings[0].averages.KILLS")
                .isEqualTo(16.5);
        // An average has no total: adding it up across games means nothing.
        assertThat(leaderboard).bodyJson().doesNotHavePath("$.standings[0].totals.ADR");
        assertThat(leaderboard)
                .bodyJson()
                .extractingPath("$.standings[0].playerId")
                .isEqualTo(kestrelId.toString());
        assertThat(leaderboard)
                .bodyJson()
                .extractingPath("$.bonuses[*].code")
                .isEqualTo(List.of("FB", "LD", "MA", "MP", "MD"));
    }

    @Test
    void anotherOrganisationsSeasonThreeIsItsOwn() {
        assertThat(get("/api/orgs/lyon-game-bar/seasons/3/leaderboard"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.standings[*].riotId")
                .isEqualTo(List.of("Osprey#EUW"));
        assertThat(get("/api/orgs/alsace-arena/seasons/1/leaderboard"))
                .bodyJson()
                .extractingPath("$.standings[*].riotId")
                .isEqualTo(List.of("Wren#EUW"));
    }

    @Test
    void summarisesASeason() {
        assertThat(get("/api/orgs/alsace-arena/seasons/3/summary"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"matchCount": 8, "matchesPlayed": 1, "gamesPlayed": 2, "players": 3,
                         "nextMatch": {"matchNumber": 2, "matchDate": "2099-01-01"}}""");
        assertThat(get("/api/orgs/alsace-arena/seasons/1/summary"))
                .bodyJson()
                .extractingPath("$.nextMatch")
                .isNull();
    }

    @Test
    void listsTheSeasonsMatchesPlayedAndPlanned() {
        assertThat(get("/api/orgs/alsace-arena/seasons/3/matches"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"matchNumber": 1, "matchDate": "2026-10-02", "games": 2, "players": 3,
                          "topPlayer": {"riotId": "Kestrel#EUW", "points": 669}},
                         {"matchNumber": 2, "matchDate": "2099-01-01", "games": 0, "players": 0, "topPlayer": null}]""");
    }

    @Test
    void showsAMatchGameByGame() {
        MvcTestResult match = get("/api/orgs/alsace-arena/seasons/3/matches/1");

        assertThat(match).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"season": {"number": 3, "name": "Season 3"}, "matchNumber": 1, "matchDate": "2026-10-02",
                 "games": [
                   {"gameNumber": 1, "lines": [
                     {"riotId": "Kestrel#EUW", "win": true, "bonuses": ["FB", "MP"], "points": 387,
                      "stats": {"PERFORMANCE_SCORE": 240, "ADR": 152.3, "KILLS": 18, "DEATHS": 12, "ASSISTS": 5,
                                "ECON_RATING": 60, "FIRST_BLOODS": 3, "PLANTS": 2, "DEFUSES": 1}},
                     {"riotId": "Wren#EUW", "win": false, "bonuses": ["LD", "MA", "MD"], "points": 263}]},
                   {"gameNumber": 2, "lines": [
                     {"riotId": "Kestrel#EUW", "win": false, "points": 282},
                     {"riotId": "Heron#EUW", "win": true, "points": 261}]}],
                 "combined": [
                   {"rank": 1, "riotId": "Kestrel#EUW", "points": 669},
                   {"rank": 2, "riotId": "Wren#EUW", "points": 263},
                   {"rank": 3, "riotId": "Heron#EUW", "points": 261}]}""");
        assertThat(match).bodyJson().extractingPath("$.columns[1]").isEqualTo("ADR");
        assertThat(match).bodyJson().extractingPath("$.bonuses.length()").isEqualTo(5);
    }

    @Test
    void aPlannedMatchHasNoGamesYet() {
        assertThat(get("/api/orgs/alsace-arena/seasons/3/matches/2"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"matchNumber": 2, "matchDate": "2099-01-01", "columns": [], "games": [], "combined": []}""");
    }

    @ParameterizedTest
    @CsvSource({
        "/api/orgs/nowhere, There's no organisation called nowhere",
        "/api/orgs/nowhere/seasons, There's no organisation called nowhere",
        "/api/orgs/closed-bar, There's no organisation called closed-bar",
        "/api/orgs/closed-bar/seasons/1/leaderboard, There's no organisation called closed-bar",
        "/api/orgs/alsace-arena/seasons/2/rules, alsace-arena has no season 2",
        "/api/orgs/alsace-arena/seasons/2/leaderboard, alsace-arena has no season 2",
        "/api/orgs/alsace-arena/seasons/2/summary, alsace-arena has no season 2",
        "/api/orgs/alsace-arena/seasons/2/matches, alsace-arena has no season 2",
        "/api/orgs/alsace-arena/seasons/3/matches/9, Season 3 has no match 9",
        "/api/orgs/lyon-game-bar/seasons/1/leaderboard, lyon-game-bar has no season 1",
    })
    void answersNotFoundWithAProblemDetail(String uri, String detail) {
        assertThat(get(uri))
                .hasStatus(404)
                .bodyJson()
                .isLenientlyEqualTo("{\"status\": 404, \"detail\": \"" + detail + "\"}");
    }

    @Test
    void refusesASeasonThatIsNotANumber() {
        assertThat(get("/api/orgs/alsace-arena/seasons/three/leaderboard")).hasStatus(400);
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    private <T> T save(T entity) {
        em.persist(entity);
        return entity;
    }

    private static void award(GameStat line, Season season, String... codes) {
        for (SeasonBonus bonus : season.getBonuses()) {
            if (List.of(codes).contains(bonus.getCode())) {
                line.award(bonus);
            }
        }
    }

    /** Every stat adds except deaths, which subtract; five bonuses at 10; 50 per win. */
    private static Season todaysRules(UUID organizationId, int number) {
        Season season = new Season(organizationId, number, "Season " + number, 8, new GameRange(1, 5), 50);
        for (Stat stat : List.of(
                Stat.PERFORMANCE_SCORE,
                Stat.KILLS,
                Stat.ASSISTS,
                Stat.ECON_RATING,
                Stat.FIRST_BLOODS,
                Stat.PLANTS,
                Stat.DEFUSES)) {
            season.setStatRule(stat, new StatRule(true, 1));
        }
        season.setStatRule(Stat.DEATHS, new StatRule(true, -1));
        season.addBonus("FB", "Most first bloods", BonusAward.HIGHEST, Stat.FIRST_BLOODS, 10);
        season.addBonus("LD", "Fewest deaths", BonusAward.LOWEST, Stat.DEATHS, 10);
        season.addBonus("MA", "Most assists", BonusAward.HIGHEST, Stat.ASSISTS, 10);
        season.addBonus("MP", "Most plants", BonusAward.HIGHEST, Stat.PLANTS, 10);
        season.addBonus("MD", "Most defuses", BonusAward.HIGHEST, Stat.DEFUSES, 10);
        return season;
    }

    private static StatLine line(
            int performanceScore,
            int kills,
            int deaths,
            int assists,
            int econRating,
            int firstBloods,
            int plants,
            int defuses) {
        return new StatLine(
                performanceScore,
                null,
                null,
                null,
                null,
                kills,
                deaths,
                assists,
                econRating,
                firstBloods,
                plants,
                defuses,
                null,
                null);
    }
}
