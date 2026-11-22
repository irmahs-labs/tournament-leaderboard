package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.as;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The platform admin's Organisations screen, on a real Postgres. Each test rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PlatformIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String API = "/api/platform/organizations";
    static final OidcLoginRequestPostProcessor PLATFORM = as("google-platform");

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
        em.flush();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
        accounts.signIn(new GoogleProfile("google-platform", "platform@example.com", "Irma"));
        jdbc.sql("UPDATE organizations SET created_at = :created, last_activity_at = :active WHERE id = :id")
                .param("created", Timestamp.from(Instant.parse("2026-11-01T10:00:00Z")))
                .param("active", Timestamp.from(Instant.parse("2026-11-18T20:00:00Z")))
                .param("id", alsace.getId())
                .update();
        jdbc.sql("UPDATE organizations SET created_at = :created WHERE id = :id")
                .param("created", Timestamp.from(Instant.parse("2026-11-05T10:00:00Z")))
                .param("id", lyon.getId())
                .update();
        jdbc.sql("UPDATE organization_members SET is_owner = true WHERE organization_id = :id AND role = 'ADMIN'")
                .param("id", lyon.getId())
                .update();
        em.flush();
        em.clear();
    }

    @Test
    void thePlatformAdminSeesEveryOrganisation() {
        assertThat(call(mvc, HttpMethod.GET, API, null, PLATFORM))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        [{"slug": "alsace-arena", "name": "Alsace Arena", "ownerEmail": null,
                          "createdAt": "2026-11-01T10:00:00Z", "lastActivityAt": "2026-11-18T20:00:00Z",
                          "members": 2, "status": "ACTIVE", "featured": true, "games": 0, "admins": 1},
                         {"slug": "lyon-game-bar", "name": "Lyon Game Bar", "ownerEmail": "lyon@example.com",
                          "createdAt": "2026-11-05T10:00:00Z", "lastActivityAt": null,
                          "members": 1, "status": "ACTIVE", "featured": false, "games": 0, "admins": 1}]""");
    }

    @Test
    void nobodyElseSeesThem() {
        assertThat(call(mvc, HttpMethod.GET, API, null, null)).hasStatus(401);
        assertThat(call(mvc, HttpMethod.GET, API, null, ADMIN)).hasStatus(403);
        assertThat(call(mvc, HttpMethod.GET, API, null, NOBODY)).hasStatus(403);
        assertThat(call(mvc, HttpMethod.PUT, API + "/lyon-game-bar/status", "{\"status\": \"SUSPENDED\"}", ADMIN))
                .hasStatus(403);
    }

    @Test
    void aSuspendedOrganisationIsNotFoundUntilReactivated() {
        String suspend = "{\"status\": \"SUSPENDED\"}";

        assertThat(call(mvc, HttpMethod.PUT, API + "/alsace-arena/status", suspend, PLATFORM))
                .hasStatus(204);
        em.flush();
        em.clear();
        assertThat(call(mvc, HttpMethod.GET, "/api/orgs/alsace-arena", null, null))
                .hasStatus(404);
        assertThat(call(mvc, HttpMethod.GET, "/api/featured", null, null)).hasStatus(204);
        assertThat(call(mvc, HttpMethod.GET, "/api/me", null, ADMIN)).bodyJson().isLenientlyEqualTo("""
                        {"memberships": [],
                         "suspended": [{"slug": "alsace-arena", "name": "Alsace Arena", "role": "ADMIN"}]}""");
        assertThat(call(mvc, HttpMethod.GET, API, null, PLATFORM)).bodyJson().isLenientlyEqualTo("""
                        [{"slug": "alsace-arena", "status": "SUSPENDED", "featured": false},
                         {"slug": "lyon-game-bar", "status": "ACTIVE"}]""");

        assertThat(call(mvc, HttpMethod.PUT, API + "/alsace-arena/status", "{\"status\": \"ACTIVE\"}", PLATFORM))
                .hasStatus(204);
        em.flush();
        em.clear();
        assertThat(call(mvc, HttpMethod.GET, "/api/orgs/alsace-arena", null, null))
                .hasStatusOk();
        assertThat(call(mvc, HttpMethod.GET, "/api/me", null, ADMIN))
                .bodyJson()
                .isLenientlyEqualTo("{\"memberships\": [{\"slug\": \"alsace-arena\"}], \"suspended\": []}");
    }

    @Test
    void anUnknownOrganisationOrStatusIsRefused() {
        assertThat(call(mvc, HttpMethod.PUT, API + "/nowhere/status", "{\"status\": \"SUSPENDED\"}", PLATFORM))
                .hasStatus(404);
        assertThat(call(mvc, HttpMethod.PUT, API + "/alsace-arena/status", "{}", PLATFORM))
                .hasStatus(400);
    }

    @Test
    void anOrganisationNobodyUsesYetIsDeletedOnceItsNameIsTyped() {
        UUID alsace = jdbc.sql("SELECT id FROM organizations WHERE slug = 'alsace-arena'")
                .query(UUID.class)
                .single();
        Season season = new Season(alsace, 1, "Season 1", 6, new GameRange(1, 3), 50);
        em.persist(season);
        em.persist(new Player(alsace, "Kestrel#EUW", null, null));
        em.flush();
        jdbc.sql("UPDATE organization_members SET player_id = (SELECT id FROM players WHERE riot_id = 'Kestrel#EUW')"
                        + " WHERE organization_id = :org AND role = 'PLAYER'")
                .param("org", alsace)
                .update();
        jdbc.sql("INSERT INTO matches (organization_id, season_id, match_number) VALUES (:org, :season, 1)")
                .param("org", alsace)
                .param("season", season.getId())
                .update();
        em.clear();
        String alsaceApi = API + "/alsace-arena";

        assertThat(call(mvc, HttpMethod.DELETE, alsaceApi, "{\"name\": \"Alsace\"}", PLATFORM))
                .hasStatus(400);
        assertThat(call(mvc, HttpMethod.DELETE, alsaceApi, "{\"name\": \"Alsace Arena\"}", ADMIN))
                .hasStatus(403);
        assertThat(call(mvc, HttpMethod.DELETE, API + "/nowhere", "{\"name\": \"Nowhere\"}", PLATFORM))
                .hasStatus(404);
        assertThat(call(mvc, HttpMethod.DELETE, alsaceApi, "{\"name\": \" Alsace Arena \"}", PLATFORM))
                .hasStatus(204);

        assertThat(call(mvc, HttpMethod.GET, "/api/orgs/alsace-arena", null, null))
                .hasStatus(404);
        assertThat(jdbc.sql("SELECT count(*) FROM players WHERE organization_id = :org")
                        .param("org", alsace)
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(call(mvc, HttpMethod.GET, API, null, PLATFORM))
                .bodyJson()
                .isLenientlyEqualTo("[{\"slug\": \"lyon-game-bar\"}]");
    }

    @Test
    void anOrganisationWithAGameOrASecondAdminGoesOnlyOnceThatIsConfirmed() {
        UUID lyon = jdbc.sql("SELECT id FROM organizations WHERE slug = 'lyon-game-bar'")
                .query(UUID.class)
                .single();
        jdbc.sql("""
                        INSERT INTO organization_members (user_id, organization_id, role)
                        SELECT id, :org, 'ADMIN' FROM app_users WHERE google_sub = 'google-nobody'""").param("org", lyon).update();
        UUID alsace = jdbc.sql("SELECT id FROM organizations WHERE slug = 'alsace-arena'")
                .query(UUID.class)
                .single();
        Season season = new Season(alsace, 1, "Season 1", 6, new GameRange(1, 3), 50);
        Player kestrel = new Player(alsace, "Kestrel#EUW", null, null);
        em.persist(season);
        em.persist(kestrel);
        em.flush();
        int match = jdbc.sql("INSERT INTO matches (organization_id, season_id, match_number) VALUES (:org, :season, 1)"
                        + " RETURNING id")
                .param("org", alsace)
                .param("season", season.getId())
                .query(Integer.class)
                .single();
        int game = jdbc.sql("INSERT INTO games (match_id, game_number) VALUES (:match, 1) RETURNING id")
                .param("match", match)
                .query(Integer.class)
                .single();
        jdbc.sql("INSERT INTO game_stats (game_id, player_id) VALUES (:game, :player)")
                .param("game", game)
                .param("player", kestrel.getId())
                .update();
        em.clear();

        assertThat(call(mvc, HttpMethod.GET, API, null, PLATFORM)).bodyJson().isLenientlyEqualTo("""
                        [{"slug": "alsace-arena", "games": 1, "admins": 1}, {"slug": "lyon-game-bar", "games": 0, "admins": 2}]""");
        assertThat(call(mvc, HttpMethod.DELETE, API + "/lyon-game-bar", "{\"name\": \"Lyon Game Bar\"}", PLATFORM))
                .hasStatus(409);
        assertThat(call(mvc, HttpMethod.DELETE, API + "/alsace-arena", "{\"name\": \"Alsace Arena\"}", PLATFORM))
                .hasStatus(409);
        assertThat(call(
                        mvc,
                        HttpMethod.DELETE,
                        API + "/alsace-arena",
                        "{\"name\": \"Alsace Arena\", \"notEmpty\": true}",
                        PLATFORM))
                .hasStatus(204);
        assertThat(jdbc.sql("SELECT count(*) FROM game_stats").query(Long.class).single())
                .isZero();
        assertThat(call(mvc, HttpMethod.GET, API, null, PLATFORM))
                .bodyJson()
                .isLenientlyEqualTo("[{\"slug\": \"lyon-game-bar\"}]");
    }

    @Test
    void aPlayerWhoNeverPlayedIsDeletedByThePlatformAdminOnly() throws Exception {
        UUID alsace = jdbc.sql("SELECT id FROM organizations WHERE slug = 'alsace-arena'")
                .query(UUID.class)
                .single();
        Season season = new Season(alsace, 1, "Season 1", 6, new GameRange(1, 3), 50);
        Player kestrel = new Player(alsace, "Kestrel#EUW", null, null);
        Player wren = new Player(alsace, "Wren#EUW", null, null);
        em.persist(season);
        em.persist(kestrel);
        em.persist(wren);
        em.flush();
        jdbc.sql("UPDATE organization_members SET player_id = :player WHERE organization_id = :org AND role = 'PLAYER'")
                .param("player", wren.getId())
                .param("org", alsace)
                .update();
        int match = jdbc.sql("INSERT INTO matches (organization_id, season_id, match_number) VALUES (:org, :season, 1)"
                        + " RETURNING id")
                .param("org", alsace)
                .param("season", season.getId())
                .query(Integer.class)
                .single();
        int game = jdbc.sql("INSERT INTO games (match_id, game_number) VALUES (:match, 1) RETURNING id")
                .param("match", match)
                .query(Integer.class)
                .single();
        jdbc.sql("INSERT INTO game_stats (game_id, player_id) VALUES (:game, :player)")
                .param("game", game)
                .param("player", kestrel.getId())
                .update();
        em.clear();
        String players = API + "/alsace-arena/players/";

        assertThat(call(mvc, HttpMethod.GET, "/api/orgs/alsace-arena/admin/players", null, ADMIN))
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"riotId": "Kestrel#EUW", "played": true}, {"riotId": "Wren#EUW", "played": false, "linked": true}]""");
        assertThat(call(mvc, HttpMethod.DELETE, players + wren.getId(), null, ADMIN))
                .hasStatus(403);
        assertThat(call(mvc, HttpMethod.DELETE, players + kestrel.getId(), null, PLATFORM))
                .hasStatus(409);
        assertThat(call(mvc, HttpMethod.DELETE, API + "/lyon-game-bar/players/" + wren.getId(), null, PLATFORM))
                .hasStatus(404);
        assertThat(call(mvc, HttpMethod.DELETE, players + wren.getId(), null, PLATFORM))
                .hasStatus(204);

        assertThat(call(mvc, HttpMethod.GET, "/api/orgs/alsace-arena/admin/players", null, ADMIN))
                .bodyJson()
                .isLenientlyEqualTo("[{\"riotId\": \"Kestrel#EUW\"}]");
        assertThat(jdbc.sql("SELECT count(*) FROM organization_members WHERE organization_id = :org")
                        .param("org", alsace)
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }
}
