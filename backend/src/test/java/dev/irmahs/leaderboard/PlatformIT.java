package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.as;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import dev.irmahs.leaderboard.organizations.Organization;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
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
                          "members": 2, "status": "ACTIVE", "featured": true},
                         {"slug": "lyon-game-bar", "name": "Lyon Game Bar", "ownerEmail": "lyon@example.com",
                          "createdAt": "2026-11-05T10:00:00Z", "lastActivityAt": null,
                          "members": 1, "status": "ACTIVE", "featured": false}]""");
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
}
