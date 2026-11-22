package dev.irmahs.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The Enter scores API on a real Postgres. Alsace Arena's season 3 counts with today's rules,
 * records ADR without counting it, doesn't record headshot %, and has a manual MVP bonus at 25.
 * admin@example.com is an Alsace admin through the test configuration's initial members. Each
 * test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ScoresIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String SEASON = "/api/orgs/alsace-arena/admin/seasons/3";
    static final String GAME = SEASON + "/matches/1/games/1";

    /** Kestrel and a new player, Newbie. Worked out by hand below. */
    static final String TWO_LINES = """
            {"matchDate": "2026-10-02", "lines": [
              {"playerId": "%s", "win": true, "manualBonuses": ["mvp"],
               "stats": {"PERFORMANCE_SCORE": 240, "ADR": 152.3, "KILLS": 18, "DEATHS": 12, "ASSISTS": 5,
                         "ECON_RATING": 60, "FIRST_BLOODS": 3, "PLANTS": 2, "DEFUSES": 1}},
              {"riotId": " Newbie#EUW ", "win": false,
               "stats": {"PERFORMANCE_SCORE": 180, "KILLS": 12, "DEATHS": 9, "ASSISTS": 8,
                         "ECON_RATING": 40, "FIRST_BLOODS": 1, "PLANTS": 0, "DEFUSES": 1}}]}""";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountService accounts;

    UUID alsace;
    UUID kestrel;
    UUID lyonPlayer;

    @BeforeEach
    void seed() {
        Organization alsaceArena = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        Organization lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsaceArena);
        em.persist(lyon);
        alsace = alsaceArena.getId();
        Player player = new Player(alsace, "Kestrel#EUW", null, null);
        em.persist(player);
        kestrel = player.getId();
        Player osprey = new Player(lyon.getId(), "Osprey#EUW", null, null);
        em.persist(osprey);
        lyonPlayer = osprey.getId();

        Season season = new Season(alsace, 3, "Season 3", 8, new GameRange(1, 5), 50);
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
        season.setStatRule(Stat.ADR, new StatRule(true, 0));
        season.setStatRule(Stat.HEADSHOT_PCT, new StatRule(false, 0));
        season.addBonus("FB", "Most first bloods", BonusAward.HIGHEST, Stat.FIRST_BLOODS, 10);
        season.addBonus("LD", "Fewest deaths", BonusAward.LOWEST, Stat.DEATHS, 10);
        season.addBonus("MA", "Most assists", BonusAward.HIGHEST, Stat.ASSISTS, 10);
        season.addBonus("MP", "Most plants", BonusAward.HIGHEST, Stat.PLANTS, 10);
        season.addBonus("MD", "Most defuses", BonusAward.HIGHEST, Stat.DEFUSES, 10);
        season.addBonus("MVP", "Most valuable player", BonusAward.MANUAL, null, 25);
        em.persist(season);
        em.flush();

        accounts.signIn(new GoogleProfile("google-admin", "admin@example.com", "Admin"));
        AppUser playerAccount = accounts.signIn(new GoogleProfile("google-player", "player@example.com", "Player"));
        jdbc.sql("INSERT INTO organization_members (user_id, organization_id, role) VALUES (:user, :org, 'PLAYER')")
                .param("user", playerAccount.getId())
                .param("org", alsace)
                .update();
        AppUser lyonAdmin = accounts.signIn(new GoogleProfile("google-lyon", "lyon@example.com", "Lyon"));
        em.persist(OrganizationMember.admin(lyonAdmin.getId(), lyon.getId()));
        accounts.signIn(new GoogleProfile("google-nobody", "nobody@example.com", "Nobody"));
        em.flush();
    }

    /** The database as the requests left it: what Hibernate still holds is written first. */
    private JdbcClient flushed() {
        em.flush();
        return jdbc;
    }

    private static OidcLoginRequestPostProcessor as(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub));
    }

    private static final OidcLoginRequestPostProcessor ADMIN = as("google-admin");

    private MvcTestResult put(String uri, String body) {
        return mvc.put()
                .uri(uri)
                .with(ADMIN)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult saveTwoLines() {
        return put(GAME, TWO_LINES.formatted(kestrel));
    }

    static Stream<Arguments> adminRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, GAME, null),
                Arguments.of(HttpMethod.PUT, GAME, "{\"lines\": [{\"riotId\": \"Abe#ONE\", \"stats\": {}}]}"),
                Arguments.of(HttpMethod.DELETE, GAME, null),
                Arguments.of(HttpMethod.GET, GAME + "/previous-roster", null),
                Arguments.of(HttpMethod.POST, SEASON + "/bonus-check", "[{\"stats\": {\"KILLS\": 3}}]"),
                Arguments.of(HttpMethod.PUT, SEASON + "/rules", "{}"),
                Arguments.of(HttpMethod.POST, SEASON + "/rules/platform-defaults", null),
                Arguments.of(
                        HttpMethod.POST,
                        SEASON + "/rules/bonuses",
                        "{\"label\": \"Kill leader\", \"award\": \"HIGHEST\", \"stat\": \"KILLS\", \"points\": 5}"),
                Arguments.of(HttpMethod.DELETE, SEASON + "/rules/bonuses/MD", null),
                Arguments.of(HttpMethod.PUT, SEASON + "/rules?apply=next", "{}"),
                Arguments.of(HttpMethod.GET, "/api/orgs/alsace-arena/admin/next-season-rules", null),
                Arguments.of(HttpMethod.DELETE, "/api/orgs/alsace-arena/admin/next-season-rules", null));
    }

    private MvcTestResult call(HttpMethod method, String uri, String body, OidcLoginRequestPostProcessor who) {
        var request = mvc.method(method).uri(uri).with(csrf());
        if (who != null) {
            request = request.with(who);
        }
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void everyRouteIsForTheOrganisationsAdminsOnly(HttpMethod method, String uri, String body) {
        assertThat(call(method, uri, body, null)).hasStatus(401);
        assertThat(call(method, uri, body, as("google-nobody"))).hasStatus(403);
        assertThat(call(method, uri, body, as("google-player"))).hasStatus(403);
        assertThat(call(method, uri, body, as("google-lyon"))).hasStatus(403);
        assertThat(call(method, uri, body, ADMIN)).hasStatus2xxSuccessful();
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void changesNeedTheCsrfToken(HttpMethod method, String uri, String body) {
        var request = mvc.method(method).uri(uri).with(ADMIN);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        MvcTestResult result = request.exchange();
        if (method == HttpMethod.GET) {
            assertThat(result).hasStatus2xxSuccessful();
        } else {
            assertThat(result).hasStatus(403);
        }
    }

    @Test
    void savesAGameWithItsBonusesAndPoints() {
        assertThat(saveTwoLines()).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"season": 3, "matchNumber": 1, "gameNumber": 1, "matchDate": "2026-10-02", "saved": true,
                 "lines": [
                   {"riotId": "Kestrel#EUW", "win": true, "bonuses": ["FB", "MD", "MP", "MVP"], "points": 422,
                    "stats": {"PERFORMANCE_SCORE": 240, "ADR": 152.3, "KILLS": 18, "DEATHS": 12}},
                   {"riotId": "Newbie#EUW", "win": false, "bonuses": ["LD", "MA", "MD"], "points": 263}]}""");

        assertThat(flushed()
                        .sql("SELECT count(*) FROM players WHERE riot_id = 'Newbie#EUW' AND organization_id = :org")
                        .param("org", alsace)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(flushed()
                        .sql("SELECT last_activity_at IS NOT NULL FROM organizations WHERE id = :org")
                        .param("org", alsace)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(mvc.get().uri(GAME).with(ADMIN))
                .bodyJson()
                .extractingPath("$.lines[*].points")
                .isEqualTo(List.of(422, 263));
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/3/leaderboard"))
                .bodyJson()
                .extractingPath("$.standings[*].points")
                .isEqualTo(List.of(422, 263));
    }

    @Test
    void savingAgainReplacesTheGamesLines() {
        saveTwoLines();
        assertThat(put(GAME, """
                        {"lines": [{"riotId": "newbie#euw", "win": true, "stats": {"KILLS": 7}}]}""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"matchDate": "2026-10-02",
                         "lines": [{"riotId": "Newbie#EUW", "bonuses": [], "points": 57}]}""");
        assertThat(flushed()
                        .sql("SELECT count(*) FROM game_stats")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(flushed()
                        .sql("SELECT count(*) FROM players WHERE lower(riot_id) = 'newbie#euw'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void loadsAGameThatHasNotBeenSaved() {
        assertThat(mvc.get().uri(GAME).with(ADMIN)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        {"season": 3, "matchNumber": 1, "gameNumber": 1, "matchDate": null, "saved": false, "lines": []}""");
    }

    @Test
    void clearingRemovesTheGameButKeepsTheMatch() {
        saveTwoLines();

        assertThat(mvc.delete().uri(GAME).with(ADMIN).with(csrf())).hasStatus(204);
        assertThat(mvc.get().uri(GAME).with(ADMIN))
                .bodyJson()
                .isLenientlyEqualTo("{\"saved\": false, \"matchDate\": \"2026-10-02\", \"lines\": []}");
        assertThat(flushed().sql("SELECT count(*) FROM games").query(Long.class).single())
                .isZero();
        assertThat(mvc.delete().uri(SEASON + "/matches/2/games/1").with(ADMIN).with(csrf()))
                .hasStatus(204);
    }

    @Test
    void carriesThePreviousGamesPlayersOver() {
        saveTwoLines();

        assertThat(mvc.get().uri(SEASON + "/matches/2/games/1/previous-roster").with(ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"matchNumber": 1, "gameNumber": 1,
                         "players": [{"riotId": "Kestrel#EUW"}, {"riotId": "Newbie#EUW"}]}""");
        assertThat(mvc.get().uri(GAME + "/previous-roster").with(ADMIN))
                .bodyJson()
                .isStrictlyEqualTo("{\"matchNumber\": null, \"gameNumber\": null, \"players\": []}");
    }

    @Test
    void checksBonusesWithoutSavingAnything() {
        assertThat(mvc.post()
                        .uri(SEASON + "/bonus-check")
                        .with(ADMIN)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"stats": {"KILLS": 18, "DEATHS": 12, "ASSISTS": 5, "FIRST_BLOODS": 3}},
                                 {"stats": {"KILLS": 12, "DEATHS": 9, "ASSISTS": 8, "FIRST_BLOODS": 1}}]"""))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("[[\"FB\"], [\"LD\", \"MA\"]]");
        assertThat(flushed().sql("SELECT count(*) FROM games").query(Long.class).single())
                .isZero();
    }

    @Test
    void changingTheRulesReawardsTheSavedGamesAndKeepsWhatTheyNoLongerRecordOutOfThePoints() {
        saveTwoLines();

        assertThat(post(SEASON + "/rules/platform-defaults")).hasStatusOk();

        // Performance score and economy no longer count, and MVP is now worked out from the place,
        // which these lines don't have, so the manual MVP Kestrel had is gone.
        assertThat(mvc.get().uri(GAME).with(ADMIN)).bodyJson().isLenientlyEqualTo("""
                {"lines": [{"riotId": "Kestrel#EUW", "bonuses": ["FB", "MD", "MP"], "points": 130.67},
                           {"riotId": "Newbie#EUW", "bonuses": ["LD", "MA", "MD"], "points": 73.22}]}""");
    }

    @Test
    void theBestPlaceOnEachTeamIsMvpAndSvp() {
        post(SEASON + "/rules/platform-defaults");

        assertThat(put(GAME, """
                        {"lines": [
                          {"riotId": "Kestrel#EUW", "win": false, "stats": {"PLACE": 1, "KILLS": 20, "DEATHS": 10}},
                          {"riotId": "Wren#EUW", "win": true, "stats": {"PLACE": 2, "KILLS": 15, "DEATHS": 12}},
                          {"riotId": "Heron#EUW", "win": true, "stats": {"PLACE": 3, "KILLS": 9, "DEATHS": 14}}]}""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"lines": [{"riotId": "Wren#EUW", "bonuses": ["MVP"], "points": 85.5},
                                   {"riotId": "Heron#EUW", "bonuses": [], "points": 51.43},
                                   {"riotId": "Kestrel#EUW", "bonuses": ["LD", "SVP"], "points": 50}]}""");
    }

    private MvcTestResult post(String uri) {
        return mvc.post().uri(uri).with(ADMIN).with(csrf()).exchange();
    }

    static Stream<Arguments> refusedSaves() {
        String kills = "{\"lines\": [{\"riotId\": \"Abe#ONE\", \"stats\": {%s}}]}";
        return Stream.of(
                Arguments.of(GAME, kills.formatted("\"HEADSHOT_PCT\": 30"), "Season 3 doesn't record HEADSHOT_PCT"),
                Arguments.of(GAME, kills.formatted("\"KAST_PCT\": 70"), "Season 3 doesn't record KAST_PCT"),
                Arguments.of(GAME, kills.formatted("\"KILLS\": 2.5"), "KILLS must be a whole number"),
                Arguments.of(GAME, kills.formatted("\"DEATHS\": -1"), "DEATHS must be between 0 and 32767"),
                Arguments.of(GAME, kills.formatted("\"ADR\": 152.35"), "ADR has at most 1 decimals"),
                Arguments.of(GAME, kills.formatted("\"ADR\": 10000"), "ADR must be between 0 and 9999.9"),
                Arguments.of(SEASON + "/matches/9/games/1", kills.formatted(""), "Season 3 has matches 1 to 8"),
                Arguments.of(SEASON + "/matches/0/games/1", kills.formatted(""), "Season 3 has matches 1 to 8"),
                Arguments.of(SEASON + "/matches/1/games/6", kills.formatted(""), "Season 3 has up to 5 games a match"),
                Arguments.of(GAME, "{\"lines\": []}", "A game needs at least one player; clear it to remove it"),
                Arguments.of(GAME, "{}", "A game needs at least one player; clear it to remove it"),
                Arguments.of(GAME, "{\"lines\": [{\"riotId\": \"  \"}]}", "Each line needs a player"),
                Arguments.of(GAME, "{\"lines\": [{\"stats\": {\"KILLS\": 3}}]}", "Each line needs a player"),
                Arguments.of(
                        GAME,
                        "{\"lines\": [{\"riotId\": \"Abe#ONE\"}, {\"riotId\": \"abe#one\"}]}",
                        "abe#one is in the game twice"),
                Arguments.of(
                        GAME,
                        "{\"lines\": [{\"riotId\": \"Abe#ONE\", \"manualBonuses\": [\"FB\"]}]}",
                        "Season 3 has no manual bonus FB"),
                Arguments.of(
                        GAME,
                        "{\"lines\": [{\"riotId\": \"Newcomer\"}]}",
                        "A Riot ID is a name of 3 to 16 characters, # and a tag"));
    }

    @ParameterizedTest
    @MethodSource("refusedSaves")
    void refusesAWrongSaveAndSavesNothing(String uri, String body, String detail) {
        assertThat(put(uri, body))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .startsWith(detail);
        assertThat(flushed()
                        .sql("SELECT count(*) FROM matches")
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(flushed()
                        .sql("SELECT count(*) FROM players")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }

    @Test
    void refusesAnotherOrganisationsPlayer() {
        assertThat(put(GAME, "{\"lines\": [{\"playerId\": \"" + lyonPlayer + "\"}]}"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("There's no player " + lyonPlayer + " here");
    }

    @Test
    void refusesAStatThatDoesNotExist() {
        assertThat(put(GAME, "{\"lines\": [{\"riotId\": \"Abe#ONE\", \"stats\": {\"ACES\": 1}}]}"))
                .hasStatus(400);
    }

    @Test
    void answersNotFoundForASeasonThatDoesNotExist() {
        assertThat(mvc.get()
                        .uri("/api/orgs/alsace-arena/admin/seasons/4/matches/1/games/1")
                        .with(ADMIN))
                .hasStatus(404);
    }

    @Test
    void keepsTheMatchDateWhenASaveSaysNone() {
        saveTwoLines();
        put(GAME.replace("games/1", "games/2"), "{\"lines\": [{\"riotId\": \"Abe#ONE\"}]}");

        assertThat(flushed()
                        .sql("SELECT match_date FROM matches")
                        .query(LocalDate.class)
                        .single())
                .isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(Map.of(
                        "games",
                        flushed()
                                .sql("SELECT count(*) FROM games")
                                .query(Long.class)
                                .single()))
                .containsEntry("games", 2L);
    }

    private MvcTestResult addBonus(String body) {
        return call(HttpMethod.POST, SEASON + "/rules/bonuses", body, ADMIN);
    }

    @Test
    void anAdminsOwnBonusIsWonInTheSavedGamesStraightAway() {
        saveTwoLines();

        assertThat(addBonus("""
                        {"label": " Kill leader ", "award": "HIGHEST", "stat": "KILLS", "points": 15}""")).hasStatus(201).bodyJson().isLenientlyEqualTo("""
                        {"bonuses": [{"code": "FB"}, {"code": "LD"}, {"code": "MA"}, {"code": "MP"}, {"code": "MD"},
                          {"code": "MVP"},
                          {"code": "KL", "label": "Kill leader", "award": "HIGHEST", "stat": "KILLS", "points": 15}]}""");
        // Kestrel has the most kills: 422 + 15.
        assertThat(mvc.get().uri(GAME).with(ADMIN)).bodyJson().isLenientlyEqualTo("""
                {"lines": [{"riotId": "Kestrel#EUW", "bonuses": ["FB", "KL", "MD", "MP", "MVP"], "points": 437},
                           {"riotId": "Newbie#EUW", "bonuses": ["LD", "MA", "MD"], "points": 263}]}""");
    }

    @Test
    void codesAreMadeFromTheNameAndNumberedWhenTaken() {
        assertThat(addBonus("""
                        {"label": "Été à fond", "award": "MANUAL", "points": 5}""")).bodyJson().extractingPath("$.bonuses[6].code").isEqualTo("EAF");
        assertThat(addBonus("""
                        {"label": "Most valuable player bis", "award": "AT_LEAST", "stat": "ASSISTS",
                         "points": 5, "threshold": 10}""")).bodyJson().extractingPath("$.bonuses[7].code").isEqualTo("MVPB");
        assertThat(addBonus("""
                        {"label": "Most valuable", "award": "LOWEST", "stat": "DEATHS", "points": 5}""")).bodyJson().extractingPath("$.bonuses[8].code").isEqualTo("MV");
        assertThat(addBonus("""
                        {"label": "Most votes", "award": "MANUAL", "points": 5}""")).bodyJson().extractingPath("$.bonuses[9].code").isEqualTo("MV2");
    }

    @Test
    void removingABonusTakesItOffEveryGame() {
        saveTwoLines();

        assertThat(call(HttpMethod.DELETE, SEASON + "/rules/bonuses/md", null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.bonuses[*].code")
                .isEqualTo(List.of("FB", "LD", "MA", "MP", "MVP"));
        assertThat(call(HttpMethod.DELETE, SEASON + "/rules/bonuses/MVP", null, ADMIN))
                .hasStatusOk();
        // Both lose MD (10), and Kestrel the manual MVP (25).
        assertThat(mvc.get().uri(GAME).with(ADMIN)).bodyJson().isLenientlyEqualTo("""
                {"lines": [{"riotId": "Kestrel#EUW", "bonuses": ["FB", "MP"], "points": 387},
                           {"riotId": "Newbie#EUW", "bonuses": ["LD", "MA"], "points": 253}]}""");
        assertThat(call(HttpMethod.DELETE, SEASON + "/rules/bonuses/MD", null, ADMIN))
                .hasStatus(404);
    }

    static Stream<String> refusedBonuses() {
        return Stream.of(
                "{\"label\": \" \", \"award\": \"MANUAL\", \"points\": 5}",
                "{\"label\": \"%s\", \"award\": \"MANUAL\", \"points\": 5}".formatted("x".repeat(41)),
                "{\"label\": \"Top\", \"award\": \"TOP_WINNER\", \"stat\": \"PLACE\", \"points\": 5}",
                "{\"label\": \"HS\", \"award\": \"HIGHEST\", \"stat\": \"HEADSHOT_PCT\", \"points\": 5}",
                "{\"label\": \"Kills\", \"award\": \"HIGHEST\", \"points\": 5}",
                "{\"label\": \"Ticked\", \"award\": \"MANUAL\", \"stat\": \"KILLS\", \"points\": 5}",
                "{\"label\": \"Ten\", \"award\": \"AT_LEAST\", \"stat\": \"KILLS\", \"points\": 5}",
                "{\"label\": \"Most\", \"award\": \"HIGHEST\", \"stat\": \"KILLS\", \"points\": 5,"
                        + " \"threshold\": 3}",
                "{\"label\": \"Big\", \"award\": \"MANUAL\", \"points\": 1001}");
    }

    @ParameterizedTest
    @MethodSource("refusedBonuses")
    void aBonusThatIsntAllowedIsRefused(String body) {
        assertThat(addBonus(body)).hasStatus(400);
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/3/rules"))
                .bodyJson()
                .extractingPath("$.bonuses.length()")
                .isEqualTo(6);
    }

    @Test
    void rulesForTheNextSeasonLeaveThisSeasonAlone() {
        saveTwoLines();
        String rulesForNext = """
                {"pointsPerWin": 80, "stats": [{"stat": "DEATHS", "recorded": true, "points": -2}],
                 "bonuses": [{"code": "FB", "points": 20}]}""";

        assertThat(call(HttpMethod.PUT, SEASON + "/rules?apply=next", rulesForNext, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"pointsPerWin": 80, "bonuses": [{"code": "FB", "points": 20}, {"code": "LD"}, {"code": "MA"},
                  {"code": "MP"}, {"code": "MD"}, {"code": "MVP"}]}""");
        // Season 3 keeps its rules and its points.
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/3/rules"))
                .bodyJson()
                .extractingPath("$.bonuses[0].points")
                .isEqualTo(10);
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/3/rules"))
                .bodyJson()
                .extractingPath("$.pointsPerWin")
                .isEqualTo(50);
        assertThat(mvc.get().uri(GAME).with(ADMIN))
                .bodyJson()
                .extractingPath("$.lines[*].points")
                .isEqualTo(List.of(422, 263));
        assertThat(call(HttpMethod.GET, "/api/orgs/alsace-arena/admin/next-season-rules", null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"pointsPerWin\": 80}");
    }

    @Test
    void aNewSeasonStartsWithTheSavedRulesWhichAreThenUsedUp() {
        call(HttpMethod.PUT, SEASON + "/rules?apply=next", "{\"pointsPerWin\": 80}", ADMIN);

        assertThat(call(
                        HttpMethod.POST,
                        "/api/orgs/alsace-arena/admin/seasons",
                        "{\"useNextSeasonRules\": true}",
                        ADMIN))
                .hasStatus(201);
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/4/rules"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"pointsPerWin": 80, "bonuses": [{"code": "FB"}, {"code": "LD"}, {"code": "MA"},
                          {"code": "MP"}, {"code": "MD"}, {"code": "MVP"}]}""");
        assertThat(call(HttpMethod.GET, "/api/orgs/alsace-arena/admin/next-season-rules", null, ADMIN))
                .hasStatus(204);
        assertThat(call(
                        HttpMethod.POST,
                        "/api/orgs/alsace-arena/admin/seasons",
                        "{\"useNextSeasonRules\": true}",
                        ADMIN))
                .hasStatus(409);
    }

    @Test
    void savedRulesCanBeDiscardedAndApplyIsCurrentOrNext() {
        call(HttpMethod.PUT, SEASON + "/rules?apply=next", "{\"pointsPerWin\": 80}", ADMIN);

        assertThat(call(HttpMethod.DELETE, "/api/orgs/alsace-arena/admin/next-season-rules", null, ADMIN))
                .hasStatus(204);
        assertThat(call(HttpMethod.GET, "/api/orgs/alsace-arena/admin/next-season-rules", null, ADMIN))
                .hasStatus(204);
        assertThat(call(HttpMethod.PUT, SEASON + "/rules?apply=later", "{}", ADMIN))
                .hasStatus(400);
        assertThat(call(
                        HttpMethod.POST,
                        "/api/orgs/alsace-arena/admin/seasons",
                        "{\"useNextSeasonRules\": true, \"copyRulesFrom\": 3}",
                        ADMIN))
                .hasStatus(400);
    }
}
