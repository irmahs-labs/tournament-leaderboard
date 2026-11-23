package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.LYON_ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.PLAYER;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static dev.irmahs.leaderboard.AdminTestSupport.callWithoutCsrf;
import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.matches.Game;
import dev.irmahs.leaderboard.matches.Match;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The admin Seasons API on a real Postgres. Alsace Arena's Season 1 has 6 matches of 1 to 3 games,
 * 40 points a win, kills that add and deaths that subtract, a manual MVP bonus, and a saved game 3
 * in match 2. Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SeasonsAdminIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String SEASONS = "/api/orgs/alsace-arena/admin/seasons";
    static final String SEASON_1 = SEASONS + "/1";
    static final String SHAPE = "\"matchCount\": 6, \"minGames\": 1, \"maxGames\": 3";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountService accounts;

    @BeforeEach
    void seed() {
        Organization alsace = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        Organization lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsace);
        em.persist(lyon);
        Season season = new Season(alsace.getId(), 1, "Season 1", 6, new GameRange(1, 3), 40);
        season.setStatRule(Stat.KILLS, new StatRule(true, 1));
        season.setStatRule(Stat.DEATHS, new StatRule(true, -1));
        season.setStatRule(Stat.ADR, new StatRule(true, 0));
        season.addBonus("MVP", "Most valuable player", BonusAward.MANUAL, null, 25);
        season.addBonus("LD", "Fewest deaths", BonusAward.LOWEST, Stat.DEATHS, 10);
        em.persist(season);
        Match match = new Match(season, 2, LocalDate.of(2026, 10, 9));
        em.persist(match);
        em.persist(new Game(match, 3));
        em.persist(new Season(lyon.getId(), 1, "Lyon Season 1", 8, new GameRange(1, 5), 50));
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
    }

    private JdbcClient flushed() {
        em.flush();
        return jdbc;
    }

    private MvcTestResult send(HttpMethod method, String uri, String body) {
        return call(mvc, method, uri, body, ADMIN);
    }

    static Stream<Arguments> adminRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, SEASONS, "{}"),
                Arguments.of(HttpMethod.PUT, SEASON_1, "{\"name\": \"Season 1\", " + SHAPE + "}"),
                Arguments.of(HttpMethod.PUT, SEASON_1 + "/rules", "{}"),
                Arguments.of(HttpMethod.POST, SEASON_1 + "/rules/platform-defaults", null));
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void everyRouteIsForTheOrganisationsAdminsOnly(HttpMethod method, String uri, String body) {
        assertThat(call(mvc, method, uri, body, null)).hasStatus(401);
        assertThat(call(mvc, method, uri, body, NOBODY)).hasStatus(403);
        assertThat(call(mvc, method, uri, body, PLAYER)).hasStatus(403);
        assertThat(call(mvc, method, uri, body, LYON_ADMIN)).hasStatus(403);
        assertThat(call(mvc, method, uri, body, ADMIN)).hasStatus2xxSuccessful();
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void changesNeedTheCsrfToken(HttpMethod method, String uri, String body) {
        assertThat(callWithoutCsrf(mvc, method, uri, body)).hasStatus(403);
    }

    @Test
    void aNewSeasonWithoutACopyStartsFromThePlatformDefaults() {
        assertThat(send(HttpMethod.POST, SEASONS, "{\"startsOn\": \"2027-01-07\"}"))
                .hasStatus(201)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"number": 2, "name": "Season 2", "startsOn": "2027-01-07", "endsOn": null,
                         "matchCount": 8, "minGames": 1, "maxGames": 5, "finished": false}""");
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/2/rules"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"pointsPerWin": 50, "kdaPoints": 10,
                         "bonuses": [{"code": "FB", "points": 10}, {"code": "LD"}, {"code": "MA"}, {"code": "MP"},
                                     {"code": "MD"}, {"code": "MVP", "award": "TOP_WINNER", "points": 20},
                                     {"code": "SVP", "award": "TOP_LOSER", "points": 10},
                                     {"code": "HON", "award": "AT_LEAST", "stat": "HONOURS", "threshold": 3}]}""");
        assertThat(flushed().sql("""
                                SELECT stat FROM season_stat_rules r JOIN seasons s ON s.id = r.season_id
                                WHERE s.number = 2 AND s.name = 'Season 2' AND r.recorded ORDER BY stat""").query(String.class).list())
                .containsExactly("ASSISTS", "DEATHS", "DEFUSES", "FIRST_BLOODS", "KILLS", "PLACE", "PLANTS");
    }

    @Test
    void aNewSeasonCanCopyAnotherSeasonsRulesAndShape() {
        assertThat(send(
                        HttpMethod.POST,
                        SEASONS,
                        "{\"name\": \" Winter cup \", \"copyRulesFrom\": 1, \"maxGames\": 2}"))
                .hasStatus(201)
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"number\": 2, \"name\": \"Winter cup\", \"matchCount\": 6, \"minGames\": 1, \"maxGames\": 2}");
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/2/rules"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"pointsPerWin": 40,
                         "stats": [{"stat": "ADR", "recorded": true, "points": 0},
                                   {"stat": "KILLS", "recorded": true, "points": 1},
                                   {"stat": "DEATHS", "recorded": true, "points": -1}],
                         "bonuses": [{"code": "MVP", "award": "MANUAL", "points": 25},
                                     {"code": "LD", "award": "LOWEST", "stat": "DEATHS"}]}""");
    }

    @Test
    void seasonsAreNumberedWithinTheirOrganisation() {
        assertThat(call(mvc, HttpMethod.POST, "/api/orgs/lyon-game-bar/admin/seasons", "{}", LYON_ADMIN))
                .bodyJson()
                .extractingPath("$.number")
                .isEqualTo(2);
        assertThat(send(HttpMethod.POST, SEASONS, "{}"))
                .bodyJson()
                .extractingPath("$.number")
                .isEqualTo(2);
        assertThat(send(HttpMethod.POST, SEASONS, "{}"))
                .bodyJson()
                .extractingPath("$.number")
                .isEqualTo(3);
    }

    @Test
    void editsASeasonAndClosesIt() {
        assertThat(send(HttpMethod.PUT, SEASON_1, """
                        {"name": "Autumn", "startsOn": "2026-09-01", "endsOn": "2026-12-15",
                         "matchCount": 10, "minGames": 2, "maxGames": 4, "finished": true}""")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        {"number": 1, "name": "Autumn", "startsOn": "2026-09-01", "endsOn": "2026-12-15",
                         "matchCount": 10, "minGames": 2, "maxGames": 4, "finished": true}""");
        assertThat(send(HttpMethod.PUT, SEASON_1, "{\"name\": \"Autumn\", " + SHAPE + "}"))
                .bodyJson()
                .extractingPath("$.finished")
                .isEqualTo(true);
        assertThat(send(HttpMethod.PUT, SEASON_1, "{\"name\": \"Autumn\", \"finished\": false, " + SHAPE + "}"))
                .bodyJson()
                .extractingPath("$.finished")
                .isEqualTo(false);
    }

    @Test
    void aSeasonCantLoseAMatchOrAGameThatWasSaved() {
        assertThat(send(
                        HttpMethod.PUT,
                        SEASON_1,
                        "{\"name\": \"Season 1\", \"matchCount\": 1, \"minGames\": 1, \"maxGames\": 3}"))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Season 1 already has match 2");
        assertThat(send(
                        HttpMethod.PUT,
                        SEASON_1,
                        "{\"name\": \"Season 1\", \"matchCount\": 6, \"minGames\": 1, \"maxGames\": 2}"))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Season 1 already has a match with game 3");
        assertThat(send(
                        HttpMethod.PUT,
                        SEASON_1,
                        "{\"name\": \"Season 1\", \"matchCount\": 2, \"minGames\": 3, \"maxGames\": 3}"))
                .hasStatusOk();
    }

    static Stream<Arguments> refusedSeasons() {
        return Stream.of(
                Arguments.of("{\"name\": \"\", " + SHAPE + "}", "A season needs a name"),
                Arguments.of(
                        "{\"name\": \"" + "x".repeat(41) + "\", " + SHAPE + "}",
                        "A season's name has at most 40 characters"),
                Arguments.of("{\"name\": \"S\", \"minGames\": 1, \"maxGames\": 3}", "matchCount is required"),
                Arguments.of(
                        "{\"name\": \"S\", \"matchCount\": 21, \"minGames\": 1, \"maxGames\": 3}",
                        "A season has 1 to 20 matches"),
                Arguments.of(
                        "{\"name\": \"S\", \"matchCount\": 0, \"minGames\": 1, \"maxGames\": 3}",
                        "A season has 1 to 20 matches"),
                Arguments.of(
                        "{\"name\": \"S\", \"matchCount\": 6, \"minGames\": 4, \"maxGames\": 3}",
                        "A match has 1 to 9 games, the fewest no more than the most"),
                Arguments.of(
                        "{\"name\": \"S\", \"matchCount\": 6, \"minGames\": 1, \"maxGames\": 10}",
                        "A match has 1 to 9 games, the fewest no more than the most"),
                Arguments.of(
                        "{\"name\": \"S\", \"startsOn\": \"2026-10-01\", \"endsOn\": \"2026-09-30\", " + SHAPE + "}",
                        "A season can't end before it starts"));
    }

    @ParameterizedTest
    @MethodSource("refusedSeasons")
    void refusesAWrongEdit(String body, String detail) {
        assertThat(send(HttpMethod.PUT, SEASON_1, body))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(detail);
        assertThat(flushed()
                        .sql("SELECT name FROM seasons WHERE number = 1 AND match_count = 6")
                        .query(String.class)
                        .list())
                .containsExactly("Season 1");
    }

    @Test
    void refusesAWrongNewSeason() {
        assertThat(send(HttpMethod.POST, SEASONS, "{\"matchCount\": 30}"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A season has 1 to 20 matches");
        assertThat(send(HttpMethod.POST, SEASONS, "{\"copyRulesFrom\": 7}")).hasStatus(404);
        assertThat(flushed()
                        .sql("SELECT count(*) FROM seasons")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }

    @Test
    void anotherOrganisationsSeasonIsOutOfReach() {
        assertThat(call(
                        mvc,
                        HttpMethod.PUT,
                        "/api/orgs/lyon-game-bar/admin/seasons/1",
                        "{\"name\": \"Mine\", " + SHAPE + "}",
                        ADMIN))
                .hasStatus(403);
        assertThat(send(HttpMethod.PUT, SEASONS + "/2", "{\"name\": \"S\", " + SHAPE + "}"))
                .hasStatus(404);
    }

    @Test
    void anAdminChangesWhatEachThingIsWorth() {
        MvcTestResult result = send(HttpMethod.PUT, SEASON_1 + "/rules", """
                {"stats": [{"stat": "KILLS", "recorded": true, "points": 2.5},
                           {"stat": "DEATHS", "recorded": false, "points": -1}],
                 "pointsPerWin": 30, "kdaPoints": 10,
                 "bonuses": [{"code": "MVP", "points": 40}]}""");

        assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"pointsPerWin": 30, "kdaPoints": 10,
                 "bonuses": [{"code": "MVP", "points": 40}, {"code": "LD", "points": 10}]}""");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.stats[?(@.stat in ['KILLS', 'DEATHS', 'ADR'])].points")
                .isEqualTo(List.of(0, 2.5, -1));
        assertThat(flushed()
                        .sql("SELECT stat FROM season_stat_rules WHERE recorded ORDER BY stat")
                        .query(String.class)
                        .list())
                .containsExactly("ADR", "KILLS");
    }

    static Stream<Arguments> refusedRules() {
        return Stream.of(
                Arguments.of("{\"stats\": [{\"stat\": \"ADR\", \"recorded\": true, \"points\": 1}]}"),
                Arguments.of("{\"stats\": [{\"stat\": \"KILLS\", \"recorded\": true, \"points\": 0.125}]}"),
                Arguments.of("{\"stats\": [{\"stat\": \"KILLS\", \"recorded\": true, \"points\": 1001}]}"),
                Arguments.of("{\"stats\": [{\"recorded\": true}]}"),
                Arguments.of("{\"kdaPoints\": -1}"),
                Arguments.of("{\"pointsPerWin\": 1001}"),
                Arguments.of("{\"bonuses\": [{\"code\": \"ACE\", \"points\": 5}]}"),
                Arguments.of("{\"bonuses\": [{\"code\": \"LD\", \"points\": -5}]}"),
                Arguments.of("{\"bonuses\": [{\"code\": \"LD\", \"threshold\": 3}]}"));
    }

    @ParameterizedTest
    @MethodSource("refusedRules")
    void wrongRulesAreRefusedAndChangeNothing(String body) {
        assertThat(send(HttpMethod.PUT, SEASON_1 + "/rules", body)).hasStatus(400);
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/1/rules"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"pointsPerWin": 40, "kdaPoints": 0,
                         "bonuses": [{"code": "MVP", "points": 25}, {"code": "LD", "points": 10}]}""");
    }

    @Test
    void aFinishedSeasonsRulesCannotChange() {
        send(HttpMethod.PUT, SEASON_1, "{\"name\": \"Season 1\", \"finished\": true, " + SHAPE + "}");

        assertThat(send(HttpMethod.PUT, SEASON_1 + "/rules", "{\"pointsPerWin\": 10}"))
                .hasStatus(409);
        assertThat(send(HttpMethod.POST, SEASON_1 + "/rules/platform-defaults", null))
                .hasStatus(409);
    }

    @Test
    void thePlatformDefaultsReplaceTheRulesAndKeepTheSeasonsOtherBonuses() {
        assertThat(send(HttpMethod.POST, SEASON_1 + "/rules/platform-defaults", null))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.bonuses[*].code")
                .isEqualTo(List.of("MVP", "LD", "FB", "MA", "MP", "MD", "SVP", "HON"));
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/1/rules"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"pointsPerWin": 50, "kdaPoints": 10,
                         "bonuses": [{"code": "MVP", "award": "TOP_WINNER", "stat": "PLACE", "points": 20},
                                     {"code": "LD"}, {"code": "FB"}, {"code": "MA"}, {"code": "MP"}, {"code": "MD"},
                                     {"code": "SVP"}, {"code": "HON", "threshold": 3}]}""");
    }
}
