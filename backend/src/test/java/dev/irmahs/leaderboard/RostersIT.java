package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.LYON_ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.PLAYER;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The schedule, match rosters and players' answers, on a real Postgres. Alsace Arena has Season
 * 1, under way with 6 matches, and Season 0, over. Its player account is linked to Kestrel and
 * signed up for Season 1; Wren and Heron have no account. Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class RostersIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String ORG = "/api/orgs/alsace-arena";
    static final String SCHEDULE = ORG + "/admin/seasons/1/schedule";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountService accounts;

    UUID kestrel;
    UUID wren;
    UUID heron;

    @BeforeEach
    void seed() {
        Organization alsace = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        Organization lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsace);
        em.persist(lyon);
        Season over = new Season(alsace.getId(), 0, "Season 0", 6, new GameRange(1, 3), 50);
        over.finish(Instant.parse("2026-06-30T20:00:00Z"));
        em.persist(over);
        em.persist(new Season(alsace.getId(), 1, "Season 1", 6, new GameRange(1, 3), 50));
        Player kestrelPlayer = new Player(alsace.getId(), "Kestrel#EUW", null, null);
        Player wrenPlayer = new Player(alsace.getId(), "Wren#EUW", null, null);
        Player heronPlayer = new Player(alsace.getId(), "Heron#EUW", null, null);
        em.persist(kestrelPlayer);
        em.persist(wrenPlayer);
        em.persist(heronPlayer);
        em.flush();
        kestrel = kestrelPlayer.getId();
        wren = wrenPlayer.getId();
        heron = heronPlayer.getId();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
        jdbc.sql("""
                        UPDATE organization_members SET player_id = :player
                        WHERE organization_id = :org AND role = 'PLAYER'""").param("player", kestrel).param("org", alsace.getId()).update();
        em.clear();
        assertThat(send(HttpMethod.POST, ORG + "/seasons/1/registration", null, PLAYER))
                .hasStatusOk();
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, OidcLoginRequestPostProcessor who) {
        return call(mvc, method, uri, body, who);
    }

    private int plan(int number, String body) throws Exception {
        String schedule = send(HttpMethod.PUT, SCHEDULE + "/" + number, body, ADMIN)
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(schedule, "$.matches[" + (number - 1) + "].id");
    }

    private MvcTestResult answer(int match, String answer, OidcLoginRequestPostProcessor who) {
        return send(HttpMethod.PUT, ORG + "/matches/" + match + "/attendance", "{\"answer\": \"" + answer + "\"}", who);
    }

    private MvcTestResult setStatus(int match, UUID player, String status) {
        return send(
                HttpMethod.PUT,
                ORG + "/admin/matches/" + match + "/roster/" + player,
                "{\"status\": \"" + status + "\"}",
                ADMIN);
    }

    @Test
    void adminsPlanEveryMatchOfTheSeason() throws Exception {
        assertThat(send(HttpMethod.GET, SCHEDULE, null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"number": 1, "defaultPlaces": 10,
                         "matches": [{"number": 1, "id": null, "places": 10}, {"number": 2}, {"number": 3},
                                     {"number": 4}, {"number": 5}, {"number": 6}]}""");

        MvcTestResult planned = send(HttpMethod.PUT, SCHEDULE + "/1", """
                        {"date": "2026-12-05", "startTime": "20:30", "location": " Discord #matchs ",
                         "replyBy": "2026-12-03", "places": 8}""", ADMIN);
        assertThat(planned).hasStatusOk();
        String first = JsonPath.parse(planned.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .read("$.matches[0]", java.util.Map.class)
                .toString();
        assertThat(first)
                .contains("date=2026-12-05", "startTime=20:30:00", "location=Discord #matchs")
                .contains("replyBy=2026-12-03", "places=8", "status=PLANNED", "locked=false")
                .contains("played=false", "confirmed=0", "waiting=0");

        assertThat(send(HttpMethod.PUT, SCHEDULE + "/7", "{}", ADMIN)).hasStatus(400);
        assertThat(send(HttpMethod.PUT, SCHEDULE + "/2", "{\"places\": 1}", ADMIN))
                .hasStatus(400);
        assertThat(send(HttpMethod.PUT, SCHEDULE + "/2", """
                        {"date": "2026-12-05", "replyBy": "2026-12-06"}""", ADMIN)).hasStatus(400);
        assertThat(send(HttpMethod.PUT, ORG + "/admin/seasons/0/schedule/1", "{}", ADMIN))
                .hasStatus(409);
        assertThat(send(HttpMethod.GET, SCHEDULE, null, LYON_ADMIN)).hasStatus(403);
        assertThat(send(HttpMethod.GET, SCHEDULE, null, PLAYER)).hasStatus(403);
    }

    @Test
    void aSignedUpPlayerTakesAPlaceThenTheWaitingListOnceItsFull() throws Exception {
        int match = plan(1, "{\"date\": \"2026-12-05\", \"places\": 2}");

        assertThat(send(HttpMethod.GET, ORG + "/me/matches", null, PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"matches": [{"number": 1, "seasonNumber": 1, "places": 2, "confirmed": 0,
                                      "status": null, "position": null}]}""");

        assertThat(setStatus(match, wren, "CONFIRMED")).hasStatusOk();
        assertThat(setStatus(match, heron, "CONFIRMED")).hasStatusOk();
        assertThat(answer(match, "YES", PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"matches\": [{\"confirmed\": 2, \"status\": \"WAITING\", \"position\": 1}]}");

        assertThat(setStatus(match, kestrel, "CONFIRMED")).hasStatus(409);
        assertThat(send(HttpMethod.DELETE, ORG + "/admin/matches/" + match + "/roster/" + heron, null, ADMIN))
                .hasStatusOk();
        assertThat(answer(match, "YES", PLAYER))
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"matches\": [{\"confirmed\": 2, \"status\": \"CONFIRMED\", \"position\": null}]}");
        assertThat(answer(match, "NO", PLAYER))
                .bodyJson()
                .isLenientlyEqualTo("{\"matches\": [{\"confirmed\": 1, \"status\": \"DECLINED\"}]}");
    }

    @Test
    void theRosterShowsEveryAnswerAndWhoHasntAnswered() throws Exception {
        int match = plan(1, "{\"date\": \"2026-12-05\", \"places\": 2}");
        send(HttpMethod.POST, ORG + "/admin/seasons/1/registrations/" + wren, null, ADMIN);

        assertThat(send(HttpMethod.GET, ORG + "/admin/matches/" + match + "/roster", null, ADMIN))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"number": 1, "places": 2, "confirmed": [], "waiting": [], "declined": [],
                         "noAnswer": [{"riotId": "Kestrel#EUW", "account": true},
                                      {"riotId": "Wren#EUW", "account": false}]}""");

        answer(match, "YES", PLAYER);
        setStatus(match, heron, "WAITING");
        setStatus(match, wren, "DECLINED");
        assertThat(send(HttpMethod.GET, ORG + "/admin/matches/" + match + "/roster", null, ADMIN))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"confirmed": [{"riotId": "Kestrel#EUW", "source": "APP", "answeredBy": "Player"}],
                         "waiting": [{"riotId": "Heron#EUW", "source": "ADMIN", "answeredBy": "Admin"}],
                         "declined": [{"riotId": "Wren#EUW"}],
                         "noAnswer": []}""");
        assertThat(send(HttpMethod.GET, ORG + "/admin/matches/" + match + "/roster", null, LYON_ADMIN))
                .hasStatus(403);
    }

    @Test
    void onlySignedUpPlayersAnswerAnOpenMatch() throws Exception {
        int match = plan(1, "{\"date\": \"2026-12-05\"}");

        assertThat(answer(match, "YES", NOBODY)).hasStatus(403);
        send(HttpMethod.DELETE, ORG + "/seasons/1/registration", null, PLAYER);
        assertThat(answer(match, "YES", PLAYER)).hasStatus(409);
        assertThat(send(HttpMethod.GET, ORG + "/me/matches", null, PLAYER))
                .bodyJson()
                .extractingPath("$.matches")
                .isEqualTo(List.of());
        send(HttpMethod.POST, ORG + "/seasons/1/registration", null, PLAYER);

        assertThat(send(HttpMethod.POST, ORG + "/admin/matches/" + match + "/lock", null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"locked\": true}");
        assertThat(answer(match, "YES", PLAYER)).hasStatus(409);
        assertThat(setStatus(match, kestrel, "CONFIRMED")).hasStatusOk();
        send(HttpMethod.DELETE, ORG + "/admin/matches/" + match + "/lock", null, ADMIN);
        assertThat(answer(match, "NO", PLAYER)).hasStatusOk();

        send(HttpMethod.PUT, SCHEDULE + "/1", "{\"date\": \"2026-12-05\", \"cancelled\": true}", ADMIN);
        assertThat(answer(match, "YES", PLAYER)).hasStatus(409);
        assertThat(send(HttpMethod.GET, ORG + "/me/matches", null, PLAYER))
                .bodyJson()
                .isLenientlyEqualTo("{\"matches\": [{\"cancelled\": true}]}");
        assertThat(answer(999_999, "YES", PLAYER)).hasStatus(404);
    }

    @Test
    void placesNeverDropBelowThePlayersConfirmed() throws Exception {
        int match = plan(1, "{\"date\": \"2026-12-05\", \"places\": 3}");
        setStatus(match, kestrel, "CONFIRMED");
        setStatus(match, wren, "CONFIRMED");
        setStatus(match, heron, "CONFIRMED");

        assertThat(send(HttpMethod.PUT, SCHEDULE + "/1", "{\"places\": 2}", ADMIN))
                .hasStatus(409);
        assertThat(send(HttpMethod.PUT, SCHEDULE + "/1", "{\"places\": 41}", ADMIN))
                .hasStatus(400);
        assertThat(send(HttpMethod.PUT, SCHEDULE + "/1", "{\"places\": 4}", ADMIN))
                .hasStatusOk();
        assertThat(send(HttpMethod.GET, ORG + "/admin/matches/" + match + "/roster", null, ADMIN))
                .bodyJson()
                .isLenientlyEqualTo("{\"places\": 4}");
    }
}
