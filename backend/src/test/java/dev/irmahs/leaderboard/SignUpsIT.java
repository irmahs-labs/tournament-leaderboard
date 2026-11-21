package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.LYON_ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.PLAYER;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import jakarta.persistence.EntityManager;
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
 * Creating your own player profile and signing up for a season, on a real Postgres. Alsace Arena
 * has Season 1, over, and Season 2, under way; Kestrel and Wren are its players, neither linked.
 * Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SignUpsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String ORG = "/api/orgs/alsace-arena";
    static final String SIGN_UPS = ORG + "/admin/seasons/2/registrations";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountService accounts;

    UUID wren;

    @BeforeEach
    void seed() {
        Organization alsace = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        Organization lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsace);
        em.persist(lyon);
        Season over = new Season(alsace.getId(), 1, "Season 1", 6, new GameRange(1, 3), 50);
        over.finish(Instant.parse("2026-06-30T20:00:00Z"));
        em.persist(over);
        em.persist(new Season(alsace.getId(), 2, "Season 2", 6, new GameRange(1, 3), 50));
        em.persist(new Player(alsace.getId(), "Kestrel#EUW", "kestrel.gg", null));
        Player wrenPlayer = new Player(alsace.getId(), "Wren#EUW", null, null);
        em.persist(wrenPlayer);
        em.flush();
        wren = wrenPlayer.getId();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, OidcLoginRequestPostProcessor who) {
        return call(mvc, method, uri, body, who);
    }

    private MvcTestResult create(String riotId, OidcLoginRequestPostProcessor who) {
        return send(HttpMethod.POST, ORG + "/players/me", "{\"riotId\": \"" + riotId + "\"}", who);
    }

    private MvcTestResult signUp(OidcLoginRequestPostProcessor who) {
        return send(HttpMethod.POST, ORG + "/seasons/2/registration", null, who);
    }

    @Test
    void aMemberWhoNeverPlayedCreatesTheirProfileAtOnce() {
        assertThat(create("Moth#EUW", NOBODY)).hasStatus(403);
        assertThat(create(" ", PLAYER)).hasStatus(400);
        assertThat(create("kestrel#euw", PLAYER)).hasStatus(409);

        assertThat(create(" Moth#EUW ", PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"member\": true, \"linked\": {\"riotId\": \"Moth#EUW\", \"games\": 0}}");
        assertThat(create("Moth2#EUW", PLAYER)).hasStatus(409);
        assertThat(send(HttpMethod.GET, "/api/me", null, PLAYER))
                .bodyJson()
                .isLenientlyEqualTo("{\"riotId\": \"Moth#EUW\"}");
        assertThat(send(HttpMethod.GET, ORG + "/players", null, null))
                .bodyJson()
                .extractingPath("$[*].riotId")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains("Moth#EUW");
    }

    @Test
    void aProfileTakesTheAccountsRiotIdFromAnotherOrganisation() {
        jdbc.sql(
                        "UPDATE app_users SET riot_id = 'Heron#EUW', discord_username = 'heron' WHERE google_sub = 'google-player'")
                .update();
        em.clear();

        assertThat(create("Ignored#EUW", PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"linked\": {\"riotId\": \"Heron#EUW\", \"discordUsername\": \"heron\"}}");
    }

    @Test
    void aClaimWaitingComesFirst() {
        Player kestrel = em.createQuery("from Player where riotId = 'Kestrel#EUW'", Player.class)
                .getSingleResult();
        send(HttpMethod.POST, ORG + "/claim", "{\"playerId\": \"" + kestrel.getId() + "\"}", PLAYER);

        assertThat(create("Moth#EUW", PLAYER)).hasStatus(409);
    }

    @Test
    void aPlayerSignsUpForTheSeasonUnderWayAndWithdraws() {
        assertThat(send(HttpMethod.GET, ORG + "/me/seasons", null, NOBODY)).hasStatus(403);
        assertThat(send(HttpMethod.GET, ORG + "/me/seasons", null, PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"player": false,
                         "seasons": [{"number": 2, "name": "Season 2", "takesSignUps": true, "signedUp": false}]}""");
        assertThat(signUp(PLAYER)).hasStatus(409);

        create("Moth#EUW", PLAYER);
        assertThat(signUp(PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"player\": true, \"seasons\": [{\"number\": 2, \"signedUp\": true}]}");
        assertThat(signUp(PLAYER)).hasStatusOk();
        assertThat(send(HttpMethod.POST, ORG + "/seasons/1/registration", null, PLAYER))
                .hasStatus(409);

        assertThat(send(HttpMethod.DELETE, ORG + "/seasons/2/registration", null, PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"seasons\": [{\"signedUp\": false}]}");
        assertThat(send(HttpMethod.DELETE, ORG + "/seasons/2/registration", null, PLAYER))
                .hasStatus(404);
        assertThat(signUp(PLAYER)).hasStatusOk();
    }

    @Test
    void closedSignUpsStopPlayersButNotAdmins() {
        create("Moth#EUW", PLAYER);
        assertThat(send(HttpMethod.PUT, ORG + "/admin/seasons/2/signup", "{}", ADMIN))
                .hasStatus(400);
        assertThat(send(HttpMethod.PUT, ORG + "/admin/seasons/2/signup", "{\"open\": false}", ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"open\": false}");
        assertThat(send(HttpMethod.GET, ORG + "/me/seasons", null, PLAYER))
                .bodyJson()
                .isLenientlyEqualTo("{\"seasons\": [{\"takesSignUps\": false}]}");
        assertThat(signUp(PLAYER)).hasStatus(409);

        assertThat(send(HttpMethod.POST, SIGN_UPS + "/" + wren, null, ADMIN)).hasStatusOk();

        send(HttpMethod.PUT, ORG + "/admin/seasons/2/signup", "{\"open\": true}", ADMIN);
        assertThat(signUp(PLAYER)).hasStatusOk();

        assertThat(send(HttpMethod.PUT, ORG + "/admin/seasons/1/signup", "{\"open\": true}", ADMIN))
                .hasStatus(409);
    }

    @Test
    void adminsSignUpPlayersWithoutAnAccountAndWithdrawPeople() {
        assertThat(send(HttpMethod.POST, SIGN_UPS + "/" + wren, null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"number": 2, "open": true, "registrations": [
                          {"riotId": "Wren#EUW", "account": false, "byAdmin": true, "addedBy": "Admin"}]}""");
        assertThat(send(HttpMethod.POST, SIGN_UPS + "/" + UUID.randomUUID(), null, ADMIN))
                .hasStatus(404);

        create("Moth#EUW", PLAYER);
        signUp(PLAYER);
        assertThat(send(HttpMethod.GET, SIGN_UPS, null, ADMIN))
                .bodyJson()
                .extractingPath("$.registrations[*].riotId")
                .isEqualTo(List.of("Wren#EUW", "Moth#EUW"));

        UUID moth = em.createQuery("from Player where riotId = 'Moth#EUW'", Player.class)
                .getSingleResult()
                .getId();
        assertThat(send(HttpMethod.DELETE, SIGN_UPS + "/" + moth, null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.registrations[*].riotId")
                .isEqualTo(List.of("Wren#EUW"));
        assertThat(send(HttpMethod.DELETE, SIGN_UPS + "/" + moth, null, ADMIN)).hasStatus(404);
        assertThat(send(HttpMethod.GET, ORG + "/me/seasons", null, PLAYER))
                .bodyJson()
                .isLenientlyEqualTo("{\"seasons\": [{\"signedUp\": false}]}");

        assertThat(signUp(PLAYER)).hasStatusOk();
        assertThat(send(HttpMethod.GET, SIGN_UPS, null, ADMIN))
                .bodyJson()
                .extractingPath("$.registrations[*].riotId")
                .isEqualTo(List.of("Wren#EUW", "Moth#EUW"));
    }

    @Test
    void onlyTheOrganisationsAdminsSeeItsSignUps() {
        assertThat(send(HttpMethod.GET, SIGN_UPS, null, LYON_ADMIN)).hasStatus(403);
        assertThat(send(HttpMethod.GET, SIGN_UPS, null, PLAYER)).hasStatus(403);
        assertThat(send(HttpMethod.GET, SIGN_UPS, null, null)).hasStatus(401);
        assertThat(send(HttpMethod.GET, ORG + "/me/seasons", null, null)).hasStatus(401);
        assertThat(send(HttpMethod.POST, ORG + "/admin/seasons/1/registrations/" + wren, null, ADMIN))
                .hasStatus(409);
    }
}
