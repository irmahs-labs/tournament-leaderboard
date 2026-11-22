package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.LYON_ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.PLAYER;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
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

    @Test
    void signingInWithTheExpectedEmailLinksThePlayerAndUsesTheEmailUp() {
        jdbc.sql("UPDATE players SET expected_email = 'newcomer@example.com' WHERE id = :id")
                .param("id", wren)
                .update();
        // Forget the players loaded so far: sign-in reads Wren again, with the email just set.
        em.flush();
        em.clear();

        accounts.signIn(new GoogleProfile("google-newcomer", "Newcomer@Example.com", "Newcomer"));
        em.flush();
        em.clear();

        assertThat(send(HttpMethod.GET, ORG + "/claim", null, AdminTestSupport.as("google-newcomer")))
                .bodyJson()
                .isLenientlyEqualTo("{\"member\": true, \"linked\": {\"riotId\": \"Wren#EUW\"}}");
        assertThat(send(HttpMethod.GET, "/api/me", null, AdminTestSupport.as("google-newcomer")))
                .bodyJson()
                .isLenientlyEqualTo(
                        "{\"memberships\": [{\"slug\": \"alsace-arena\", \"role\": \"PLAYER\", \"playerId\": \"" + wren
                                + "\"}]}");
        assertThat(jdbc.sql("SELECT count(*) FROM players WHERE id = :id AND expected_email IS NULL")
                        .param("id", wren)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void aLinkedPlayerChangesOnlyTheirRankInAnOrganisation() throws Exception {
        assertThat(send(HttpMethod.PUT, ORG + "/claim/player", "{\"rankId\": null}", PLAYER))
                .hasStatus(409);
        claim(kestrel, PLAYER);
        send(HttpMethod.POST, ORG + "/admin/claims/" + waitingClaimId() + "/accept", null, ADMIN);
        em.flush();
        jdbc.sql("INSERT INTO ranks (rank_name, rank_value) VALUES ('Gold 2', 11)")
                .update();
        Integer gold = jdbc.sql("SELECT id FROM ranks WHERE rank_name = 'Gold 2'")
                .query(Integer.class)
                .single();

        assertThat(send(HttpMethod.PUT, ORG + "/claim/player", """
                        {"rankId": %d, "riotId": "Ignored#EUW"}""".formatted(gold), PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"riotId": "Kestrel#EUW", "discordUsername": "kestrel.gg", "rank": {"name": "Gold 2"},
                         "expectedEmail": null, "linked": true}""");
        assertThat(send(HttpMethod.PUT, ORG + "/claim/player", "{\"rankId\": 999}", PLAYER))
                .hasStatus(400);
        assertThat(send(HttpMethod.PUT, ORG + "/claim/player", "{\"rankId\": null}", NOBODY))
                .hasStatus(403);
    }

    @Test
    void theFirstLinkedProfileGivesTheAccountItsRiotIdAndDiscord() throws Exception {
        claim(kestrel, PLAYER);
        send(HttpMethod.POST, ORG + "/admin/claims/" + waitingClaimId() + "/accept", null, ADMIN);

        assertThat(send(HttpMethod.GET, "/api/me", null, PLAYER))
                .bodyJson()
                .isLenientlyEqualTo("{\"riotId\": \"Kestrel#EUW\", \"discordUsername\": \"kestrel.gg\"}");
    }

    @Test
    void aLaterLinkedProfileTakesTheAccountsRiotIdAndDiscord() throws Exception {
        claim(kestrel, PLAYER);
        send(HttpMethod.POST, ORG + "/admin/claims/" + waitingClaimId() + "/accept", null, ADMIN);
        linkAtLyon();
        em.flush();

        assertThat(riotIdAndDiscordOf(osprey)).isEqualTo("Kestrel#EUW kestrel.gg");
        assertThat(aliasesOf(osprey)).containsExactly("Osprey#EUW");
    }

    @Test
    void aLaterProfileKeepsItsRiotIdWhenAnotherPlayerThereHasTheAccounts() throws Exception {
        claim(kestrel, PLAYER);
        send(HttpMethod.POST, ORG + "/admin/claims/" + waitingClaimId() + "/accept", null, ADMIN);
        em.persist(new Player(
                jdbc.sql("SELECT id FROM organizations WHERE slug = 'lyon-game-bar'")
                        .query(UUID.class)
                        .single(),
                "Kestrel#EUW",
                null,
                null));
        em.flush();
        linkAtLyon();
        em.flush();

        assertThat(riotIdAndDiscordOf(osprey)).isEqualTo("Osprey#EUW kestrel.gg");
    }

    @Test
    void changingTheRiotIdOnMyAccountChangesEveryLinkedProfile() throws Exception {
        claim(kestrel, PLAYER);
        send(HttpMethod.POST, ORG + "/admin/claims/" + waitingClaimId() + "/accept", null, ADMIN);
        linkAtLyon();

        assertThat(send(HttpMethod.PUT, "/api/me/identity", """
                        {"riotId": " Kes#EUW ", "discordUsername": "kes"}""", PLAYER))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"riotId\": \"Kes#EUW\", \"discordUsername\": \"kes\"}");
        em.flush();
        assertThat(riotIdAndDiscordOf(kestrel)).isEqualTo("Kes#EUW kes");
        assertThat(riotIdAndDiscordOf(osprey)).isEqualTo("Kes#EUW kes");
        assertThat(aliasesOf(kestrel)).containsExactly("Kestrel#EUW");
        assertThat(aliasesOf(osprey)).containsExactly("Kestrel#EUW", "Osprey#EUW");
    }

    @Test
    void myRiotIdCantBeAnotherPlayersInOneOfMyOrganisations() throws Exception {
        claim(kestrel, PLAYER);
        send(HttpMethod.POST, ORG + "/admin/claims/" + waitingClaimId() + "/accept", null, ADMIN);

        assertThat(send(HttpMethod.PUT, "/api/me/identity", "{\"riotId\": \"wren#euw\"}", PLAYER))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("wren#euw is already another player at Alsace Arena");
        assertThat(send(HttpMethod.PUT, "/api/me/identity", "{\"riotId\": \" \"}", PLAYER))
                .hasStatus(400);
        assertThat(send(HttpMethod.PUT, "/api/me/identity", "{\"riotId\": \"Kestrel\"}", PLAYER))
                .hasStatus(400);
        assertThat(send(HttpMethod.PUT, "/api/me/identity", "{\"riotId\": \"Kestrel#EUW\"}", PLAYER))
                .hasStatusOk();
        assertThat(send(HttpMethod.PUT, "/api/me/identity", "{\"riotId\": \"A#1\"}", null))
                .hasStatus(401);
    }

    @Test
    void adminsCantChangeALinkedPlayersRiotIdOrDiscord() throws Exception {
        claim(kestrel, PLAYER);
        send(HttpMethod.POST, ORG + "/admin/claims/" + waitingClaimId() + "/accept", null, ADMIN);

        assertThat(send(HttpMethod.PUT, ORG + "/admin/players/" + kestrel, """
                        {"riotId": "Other#EUW", "discordUsername": "kestrel.gg"}""", ADMIN))
                .hasStatus(409);
        assertThat(send(HttpMethod.PUT, ORG + "/admin/players/" + kestrel, """
                        {"riotId": "Kestrel#EUW", "discordUsername": "kestrel.gg", "aliases": [{"alias": "K", "kind": "NICKNAME"}]}""", ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"linked\": true, \"aliases\": [{\"alias\": \"K\"}]}");
    }

    /** The player joins Lyon Game Bar and its admin accepts their claim on Osprey. */
    private void linkAtLyon() throws Exception {
        String lyon = "/api/orgs/lyon-game-bar";
        send(HttpMethod.POST, lyon + "/membership", null, PLAYER);
        send(HttpMethod.POST, lyon + "/claim", "{\"playerId\": \"" + osprey + "\"}", PLAYER);
        String body = send(HttpMethod.GET, lyon + "/admin/claims", null, LYON_ADMIN)
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        send(HttpMethod.POST, lyon + "/admin/claims/" + JsonPath.read(body, "$[0].id") + "/accept", null, LYON_ADMIN);
    }

    private String riotIdAndDiscordOf(UUID player) {
        return jdbc.sql("SELECT riot_id || ' ' || coalesce(discord_username, '-') FROM players WHERE id = :id")
                .param("id", player)
                .query(String.class)
                .single();
    }

    private List<String> aliasesOf(UUID player) {
        return jdbc.sql("SELECT alias FROM player_aliases WHERE player_id = :id ORDER BY alias")
                .param("id", player)
                .query(String.class)
                .list();
    }
}
