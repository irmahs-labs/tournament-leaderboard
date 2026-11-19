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
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
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
 * Joining as a player, claiming a profile, and the Claims queue, on a real Postgres. Alsace Arena
 * has one admin and one player; Kestrel and Wren are its unlinked players. Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class ClaimsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String ORG = "/api/orgs/alsace-arena";

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
    UUID osprey;

    @BeforeEach
    void seed() {
        Organization alsace = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        Organization lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsace);
        em.persist(lyon);
        Player kestrelPlayer = new Player(alsace.getId(), "Kestrel#EUW", "kestrel.gg", null);
        Player wrenPlayer = new Player(alsace.getId(), "Wren#EUW", null, null);
        Player ospreyPlayer = new Player(lyon.getId(), "Osprey#EUW", null, null);
        em.persist(kestrelPlayer);
        em.persist(wrenPlayer);
        em.persist(ospreyPlayer);
        em.flush();
        kestrel = kestrelPlayer.getId();
        wren = wrenPlayer.getId();
        osprey = ospreyPlayer.getId();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, OidcLoginRequestPostProcessor who) {
        return call(mvc, method, uri, body, who);
    }

    private MvcTestResult claim(UUID player, OidcLoginRequestPostProcessor who) {
        return send(
                HttpMethod.POST,
                ORG + "/claim",
                "{\"playerId\": \"" + player + "\", \"note\": \" I play Jett \"}",
                who);
    }

    private String waitingClaimId() throws Exception {
        String body = send(HttpMethod.GET, ORG + "/admin/claims", null, ADMIN)
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$[0].id");
    }

    @Test
    void someoneSignedInJoinsAsAPlayerOnce() {
        assertThat(send(HttpMethod.GET, ORG + "/claim", null, NOBODY))
                .bodyJson()
                .isLenientlyEqualTo("{\"member\": false, \"linked\": null, \"claim\": null}");
        assertThat(send(HttpMethod.POST, ORG + "/membership", null, NOBODY))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"member\": true}");
        assertThat(send(HttpMethod.POST, ORG + "/membership", null, ADMIN)).hasStatusOk();
        assertThat(send(HttpMethod.GET, "/api/me", null, ADMIN))
                .bodyJson()
                .isLenientlyEqualTo("{\"memberships\": [{\"slug\": \"alsace-arena\", \"role\": \"ADMIN\"}]}");
    }

    @Test
    void membersPickAmongThePlayersNobodyHasClaimed() {
        assertThat(send(HttpMethod.GET, ORG + "/claim/players", null, NOBODY)).hasStatus(403);
        assertThat(send(HttpMethod.GET, ORG + "/claim/players", null, PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"riotId": "Kestrel#EUW", "discordUsername": "kestrel.gg", "games": 0},
                         {"riotId": "Wren#EUW"}]""");
    }

    @Test
    void aPlayerClaimsWaitsAndCancels() {
        assertThat(claim(kestrel, PLAYER)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"member": true, "linked": null,
                         "claim": {"riotId": "Kestrel#EUW", "note": "I play Jett", "status": "PENDING"}}""");
        assertThat(claim(wren, PLAYER)).hasStatus(409);
        assertThat(send(HttpMethod.DELETE, ORG + "/claim", null, PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"claim\": null}");
        assertThat(send(HttpMethod.DELETE, ORG + "/claim", null, PLAYER)).hasStatus(404);
        assertThat(claim(wren, PLAYER)).hasStatusOk();
    }

    @Test
    void acceptingLinksTheProfileAndDeclinesTheOtherClaimsForIt() throws Exception {
        send(HttpMethod.POST, ORG + "/membership", null, NOBODY);
        claim(kestrel, PLAYER);
        claim(kestrel, NOBODY);

        assertThat(send(HttpMethod.GET, ORG + "/admin/claims", null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"name": "Player", "email": "player@example.com", "competing": 2, "own": false,
                          "player": {"riotId": "Kestrel#EUW"}},
                         {"name": "Nobody", "competing": 2}]""");

        assertThat(send(HttpMethod.POST, ORG + "/admin/claims/" + waitingClaimId() + "/accept", null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"claim\": {\"status\": \"ACCEPTED\", \"selfApproved\": false}}");
        assertThat(send(HttpMethod.GET, ORG + "/claim", null, PLAYER))
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"linked\": {\"riotId\": \"Kestrel#EUW\"}, \"claim\": {\"status\": \"ACCEPTED\"}}");
        assertThat(send(HttpMethod.GET, ORG + "/claim", null, NOBODY))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"claim": {"status": "DECLINED", "reason": "Claimed by another account"}}""");
        assertThat(send(HttpMethod.GET, ORG + "/claim/players", null, NOBODY))
                .bodyJson()
                .extractingPath("$[*].riotId")
                .isEqualTo(List.of("Wren#EUW"));
        assertThat(claim(wren, PLAYER)).hasStatus(409);
        assertThat(claim(kestrel, NOBODY)).hasStatus(409);
    }

    @Test
    void anAdminDeclinesWithAReasonThePlayerSees() throws Exception {
        claim(kestrel, PLAYER);
        String id = waitingClaimId();

        assertThat(send(
                        HttpMethod.POST,
                        ORG + "/admin/claims/" + id + "/decline",
                        "{\"reason\": \"%s\"}".formatted("x".repeat(201)),
                        ADMIN))
                .hasStatus(400);
        assertThat(send(
                        HttpMethod.POST,
                        ORG + "/admin/claims/" + id + "/decline",
                        "{\"reason\": \"Kestrel is someone else\"}",
                        ADMIN))
                .hasStatusOk();
        assertThat(send(HttpMethod.GET, ORG + "/claim", null, PLAYER))
                .bodyJson()
                .isLenientlyEqualTo("{\"claim\": {\"status\": \"DECLINED\", \"reason\": \"Kestrel is someone else\"}}");
        assertThat(send(HttpMethod.POST, ORG + "/admin/claims/" + id + "/accept", null, ADMIN))
                .hasStatus(409);
        assertThat(send(HttpMethod.GET, ORG + "/admin/claims?status=DECLINED", null, ADMIN))
                .bodyJson()
                .extractingPath("$.length()")
                .isEqualTo(1);
    }

    @Test
    void theOnlyAdminApprovesTheirOwnClaimButNotWhileAnotherAdminExists() throws Exception {
        jdbc.sql(
                        "UPDATE organization_members SET role = 'ADMIN' WHERE user_id = (SELECT id FROM app_users WHERE email = 'player@example.com')")
                .update();
        claim(wren, PLAYER);
        String own = waitingClaimId();

        assertThat(send(HttpMethod.POST, ORG + "/admin/claims/" + own + "/accept", null, PLAYER))
                .hasStatus(403);
        assertThat(send(HttpMethod.GET, ORG + "/admin/claims", null, PLAYER))
                .bodyJson()
                .isLenientlyEqualTo("[{\"own\": true}]");

        jdbc.sql(
                        "UPDATE organization_members SET role = 'PLAYER' WHERE user_id = (SELECT id FROM app_users WHERE email = 'player@example.com')")
                .update();
        send(HttpMethod.DELETE, ORG + "/claim", null, PLAYER);
        claim(kestrel, ADMIN);
        assertThat(send(HttpMethod.POST, ORG + "/admin/claims/" + waitingClaimId() + "/accept", null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"own\": true, \"claim\": {\"status\": \"ACCEPTED\", \"selfApproved\": true}}");
    }

    @Test
    void onlyItsOwnPlayersCanBeClaimedAndOnlyItsAdminsAnswer() {
        assertThat(send(HttpMethod.GET, ORG + "/claim", null, null)).hasStatus(401);
        assertThat(send(HttpMethod.GET, ORG + "/claim/players", null, null)).hasStatus(401);
        assertThat(claim(osprey, PLAYER)).hasStatus(404);
        assertThat(claim(kestrel, NOBODY)).hasStatus(403);
        assertThat(send(HttpMethod.GET, ORG + "/admin/claims", null, PLAYER)).hasStatus(403);
        assertThat(send(HttpMethod.GET, ORG + "/admin/claims", null, LYON_ADMIN))
                .hasStatus(403);
    }
}
