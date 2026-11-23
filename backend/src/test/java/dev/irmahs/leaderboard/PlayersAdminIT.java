package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.LYON_ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.PLAYER;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static dev.irmahs.leaderboard.AdminTestSupport.callWithoutCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.players.AliasKind;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.players.Player.NewAlias;
import dev.irmahs.leaderboard.ranks.Rank;
import jakarta.persistence.EntityManager;
import java.util.List;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The admin Players API on a real Postgres. Alsace Arena has Kestrel (Gold 2, Discord kes_tv, also
 * known as Birdie) and Osprey; Lyon Game Bar has its own Kestrel. Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PlayersAdminIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String PLAYERS = "/api/orgs/alsace-arena/admin/players";

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
    UUID lyonKestrel;
    int gold;

    @BeforeEach
    void seed() {
        Organization alsaceArena = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        Organization lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsaceArena);
        em.persist(lyon);
        alsace = alsaceArena.getId();
        Rank gold2 = new Rank("Gold 2", 13, "#E5B842");
        em.persist(gold2);
        em.persist(new Rank("Iron 1", 1, "#4F4F4F"));
        gold = gold2.getId();
        Player player = new Player(alsace, "Kestrel#EUW", "kes_tv", gold2);
        player.replaceAliases(List.of(new NewAlias("Birdie", AliasKind.NICKNAME)));
        em.persist(player);
        kestrel = player.getId();
        em.persist(new Player(alsace, "Osprey#EUW", null, null));
        Player other = new Player(lyon.getId(), "Kestrel#EUW", null, null);
        em.persist(other);
        lyonKestrel = other.getId();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsaceArena, lyon);
    }

    private JdbcClient flushed() {
        em.flush();
        return jdbc;
    }

    private MvcTestResult post(String body) {
        return call(mvc, HttpMethod.POST, PLAYERS, body, ADMIN);
    }

    private MvcTestResult put(UUID player, String body) {
        return call(mvc, HttpMethod.PUT, PLAYERS + "/" + player, body, ADMIN);
    }

    private MvcTestResult search(String query) {
        return mvc.get().uri(PLAYERS).param("q", query).with(ADMIN).exchange();
    }

    /** KESTREL in an address stands for Kestrel's id, which only exists once the test has started. */
    static Stream<Arguments> adminRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, PLAYERS, null),
                Arguments.of(HttpMethod.POST, PLAYERS, "{\"riotId\": \"Newbie#EUW\"}"),
                Arguments.of(HttpMethod.PUT, PLAYERS + "/KESTREL", "{\"riotId\": \"Kestrel#EUW\"}"));
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void everyRouteIsForTheOrganisationsAdminsOnly(HttpMethod method, String route, String body) {
        String uri = route.replace("KESTREL", kestrel.toString());
        assertThat(call(mvc, method, uri, body, null)).hasStatus(401);
        assertThat(call(mvc, method, uri, body, NOBODY)).hasStatus(403);
        assertThat(call(mvc, method, uri, body, PLAYER)).hasStatus(403);
        assertThat(call(mvc, method, uri, body, LYON_ADMIN)).hasStatus(403);
        assertThat(call(mvc, method, uri, body, ADMIN)).hasStatus2xxSuccessful();
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void changesNeedTheCsrfToken(HttpMethod method, String route, String body) {
        MvcTestResult result = callWithoutCsrf(mvc, method, route.replace("KESTREL", kestrel.toString()), body);
        if (method == HttpMethod.GET) {
            assertThat(result).hasStatusOk();
        } else {
            assertThat(result).hasStatus(403);
        }
    }

    @Test
    void listsTheOrganisationsPlayersByRiotId() {
        assertThat(mvc.get().uri(PLAYERS).with(ADMIN)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                [{"id": "%s", "riotId": "Kestrel#EUW", "discordUsername": "kes_tv",
                  "rank": {"id": %d, "name": "Gold 2", "color": "#E5B842"},
                  "aliases": [{"alias": "Birdie", "kind": "NICKNAME"}], "expectedEmail": null, "linked": false, "played": false},
                 {"id": "%s", "riotId": "Osprey#EUW", "discordUsername": null, "rank": null, "aliases": [],
                  "expectedEmail": null, "linked": false, "played": false}]""".formatted(
                        kestrel, gold, idOf("Osprey#EUW")));
    }

    @Test
    void searchMatchesTheRiotIdTheDiscordUsernameAndEveryAlias() {
        assertThat(search("ospr")).bodyJson().extractingPath("$[*].riotId").isEqualTo(List.of("Osprey#EUW"));
        assertThat(search("KES_")).bodyJson().extractingPath("$[*].riotId").isEqualTo(List.of("Kestrel#EUW"));
        assertThat(search(" birdie ")).bodyJson().extractingPath("$[*].riotId").isEqualTo(List.of("Kestrel#EUW"));
        assertThat(search("#euw"))
                .bodyJson()
                .extractingPath("$[*].riotId")
                .isEqualTo(List.of("Kestrel#EUW", "Osprey#EUW"));
        assertThat(search("%")).bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(search("s_tv")).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(search("s_t_")).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void addsAPlayer() {
        assertThat(post("""
                        {"riotId": " Newbie#EUW ", "discordUsername": " ", "rankId": %d,
                         "aliases": [{"alias": "Newb", "kind": "NICKNAME"}, {"alias": "newb", "kind": "OTHER"}]}""".formatted(gold))).hasStatus(201).bodyJson().isLenientlyEqualTo("""
                        {"riotId": "Newbie#EUW", "discordUsername": null, "rank": {"name": "Gold 2"},
                         "aliases": [{"alias": "Newb", "kind": "NICKNAME"}]}""");
        assertThat(flushed()
                        .sql("SELECT organization_id FROM players WHERE riot_id = 'Newbie#EUW'")
                        .query(UUID.class)
                        .single())
                .isEqualTo(alsace);
    }

    @Test
    void aRiotIdIsTakenWhateverItsLetterCaseButOnlyInItsOrganisation() {
        assertThat(post("{\"riotId\": \"kestrel#euw\"}"))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Kestrel#EUW is already a player here");
        assertThat(put(idOf("Osprey#EUW"), "{\"riotId\": \"KESTREL#EUW\"}")).hasStatus(409);
        assertThat(call(
                        mvc,
                        HttpMethod.POST,
                        "/api/orgs/lyon-game-bar/admin/players",
                        "{\"riotId\": \"Osprey#EUW\"}",
                        LYON_ADMIN))
                .hasStatus(201);
    }

    @Test
    void renamingKeepsTheOldRiotIdAsAnAliasAndSearchStillFindsIt() {
        assertThat(put(
                        kestrel,
                        "{\"riotId\": \"Falcon#EUW\", \"discordUsername\": \"kes_tv\", \"rankId\": %d}"
                                .formatted(gold)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"riotId": "Falcon#EUW", "rank": {"name": "Gold 2"},
                         "aliases": [{"alias": "Birdie", "kind": "NICKNAME"},
                                     {"alias": "Kestrel#EUW", "kind": "PREVIOUS_RIOT_ID"}]}""");
        em.flush();
        assertThat(search("kestrel")).bodyJson().extractingPath("$[*].riotId").isEqualTo(List.of("Falcon#EUW"));
    }

    @Test
    void anEditSetsEveryDetailAndReplacesTheAliasesItLists() {
        assertThat(put(kestrel, """
                        {"riotId": "Kestrel#EUW", "aliases": [{"alias": "Kes", "kind": "OTHER"}]}""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"discordUsername": null, "rank": null, "aliases": [{"alias": "Kes", "kind": "OTHER"}]}""");
        assertThat(flushed()
                        .sql("SELECT count(*) FROM player_aliases")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void aGoogleEmailCanBeTypedInAdvanceOncePerOrganisation() {
        assertThat(mvc.post()
                        .uri(PLAYERS)
                        .with(ADMIN)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riotId\": \"Swift#EUW\", \"expectedEmail\": \" Swift@Example.com \"}"))
                .hasStatus(201)
                .bodyJson()
                .extractingPath("$.expectedEmail")
                .isEqualTo("swift@example.com");
        assertThat(mvc.post()
                        .uri(PLAYERS)
                        .with(ADMIN)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riotId\": \"Hawk#EUW\", \"expectedEmail\": \"SWIFT@example.com\"}"))
                .hasStatus(409);
        assertThat(mvc.post()
                        .uri(PLAYERS)
                        .with(ADMIN)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"riotId\": \"Hawk#EUW\", \"expectedEmail\": \"not an email\"}"))
                .hasStatus(400);
    }

    @Test
    void aRiotIdTakesRiotsFormatWhenNewOrChangedOnly() {
        Player old = new Player(alsace, "Drrako", null, null);
        em.persist(old);
        em.flush();

        assertThat(post("{\"riotId\": \"Newcomer\"}")).hasStatus(400);
        assertThat(put(old.getId(), "{\"riotId\": \"Drrako\", \"discordUsername\": \"drr\"}"))
                .hasStatusOk();
        assertThat(put(old.getId(), "{\"riotId\": \"Drrako2\"}")).hasStatus(400);
        assertThat(put(old.getId(), "{\"riotId\": \"Drrako#EUW\"}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"riotId\": \"Drrako#EUW\"}");
    }

    @Test
    void anotherOrganisationsPlayerIsNotFound() {
        assertThat(put(lyonKestrel, "{\"riotId\": \"Stolen#EUW\"}")).hasStatus(404);
        assertThat(put(UUID.randomUUID(), "{\"riotId\": \"Ghost#EUW\"}")).hasStatus(404);
    }

    static Stream<Arguments> refusedDetails() {
        return Stream.of(
                Arguments.of("{}", "A player needs a Riot ID"),
                Arguments.of("{\"riotId\": \"  \"}", "A player needs a Riot ID"),
                Arguments.of("{\"riotId\": \"" + "x".repeat(101) + "\"}", "Riot ID has at most 100 characters"),
                Arguments.of("{\"riotId\": \"A#1\", \"rankId\": 999}", "There's no rank 999"),
                Arguments.of(
                        "{\"riotId\": \"A#1\", \"aliases\": [{\"alias\": \"\", \"kind\": \"OTHER\"}]}",
                        "An alias can't be empty"),
                Arguments.of(
                        "{\"riotId\": \"A#1\", \"aliases\": [{\"alias\": \"Ace\"}]}", "The alias Ace needs a kind"),
                Arguments.of(
                        "{\"riotId\": \"A#1\", \"aliases\": [{\"alias\": \"a#1\", \"kind\": \"OTHER\"}]}",
                        "a#1 is the player's Riot ID, not an alias"));
    }

    @ParameterizedTest
    @MethodSource("refusedDetails")
    void refusesWrongDetailsAndSavesNothing(String body, String detail) {
        assertThat(post(body))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(detail);
        assertThat(put(kestrel, body))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo(detail);
        assertThat(flushed()
                        .sql("SELECT count(*) FROM players")
                        .query(Long.class)
                        .single())
                .isEqualTo(3);
        assertThat(flushed()
                        .sql("SELECT riot_id FROM players WHERE id = :id")
                        .param("id", kestrel)
                        .query(String.class)
                        .single())
                .isEqualTo("Kestrel#EUW");
    }

    @Test
    void refusesAnAliasKindThatDoesNotExist() {
        assertThat(post("{\"riotId\": \"A#1\", \"aliases\": [{\"alias\": \"Ace\", \"kind\": \"SMURF\"}]}"))
                .hasStatus(400);
    }

    @Test
    void anyoneCanReadTheRanksLowestFirst() {
        assertThat(mvc.get().uri("/api/ranks"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .isEqualTo(List.of("Iron 1", "Gold 2"));
    }

    private UUID idOf(String riotId) {
        return flushed()
                .sql("SELECT id FROM players WHERE riot_id = :riotId AND organization_id = :org")
                .param("riotId", riotId)
                .param("org", alsace)
                .query(UUID.class)
                .single();
    }
}
