package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.organizations.Organization;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Creating an organisation and the home page's featured one, on a real Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class FoundingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountService accounts;

    Organization alsace;
    Organization lyon;

    @BeforeEach
    void seed() {
        alsace = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsace);
        em.persist(lyon);
        em.flush();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
    }

    private static String founding(String name, String slug) {
        return "{\"name\": \"" + name + "\", \"slug\": \"" + slug + "\"}";
    }

    @Test
    void someoneSignedInCreatesAnOrganisationAndOwnsIt() {
        assertThat(call(mvc, HttpMethod.POST, "/api/orgs", founding(" Metz Esport ", "Metz-Esport"), NOBODY))
                .hasStatus(201)
                .bodyJson()
                .isLenientlyEqualTo("{\"slug\": \"metz-esport\", \"name\": \"Metz Esport\"}");

        assertThat(call(mvc, HttpMethod.GET, "/api/me", null, NOBODY))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"memberships": [{"slug": "metz-esport", "role": "ADMIN", "owner": true}]}""");
        assertThat(call(mvc, HttpMethod.GET, "/api/orgs/metz-esport", null, null))
                .hasStatusOk();
        assertThat(jdbc.sql("SELECT count(*) FROM organizations WHERE slug = 'metz-esport' AND created_by IS NOT NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void nobodySignedInCanCreateOne() {
        assertThat(call(mvc, HttpMethod.POST, "/api/orgs", founding("Metz", "metz-esport"), null))
                .hasStatus(401);
    }

    @Test
    void anAddressIsTakenOnce() {
        assertThat(call(mvc, HttpMethod.POST, "/api/orgs", founding("Alsace", "alsace-arena"), NOBODY))
                .hasStatus(409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "-metz", "metz-", "metz--esport", "metz esport", "metz_esport", "admin", "join"})
    void addressesThatArentAllowedAreRefused(String slug) {
        assertThat(call(mvc, HttpMethod.POST, "/api/orgs", founding("Metz", slug), NOBODY))
                .hasStatus(400);
    }

    @Test
    void aNameIsRequired() {
        assertThat(call(mvc, HttpMethod.POST, "/api/orgs", founding("  ", "metz-esport"), NOBODY))
                .hasStatus(400);
    }

    @Test
    void oneAccountOwnsThreeAtMost() {
        for (String slug : new String[] {"one-org", "two-org", "three-org"}) {
            assertThat(call(mvc, HttpMethod.POST, "/api/orgs", founding("Org", slug), NOBODY))
                    .hasStatus(201);
        }
        assertThat(call(mvc, HttpMethod.POST, "/api/orgs", founding("Org", "four-org"), NOBODY))
                .hasStatus(403);
    }

    @Test
    void theHomePageFeaturesTheMostRecentlyActiveOrganisation() {
        assertThat(call(mvc, HttpMethod.GET, "/api/featured", null, null)).hasStatus(204);

        jdbc.sql("UPDATE organizations SET last_activity_at = :when WHERE id = :id")
                .param("when", java.sql.Timestamp.from(Instant.parse("2026-11-10T20:00:00Z")))
                .param("id", alsace.getId())
                .update();
        jdbc.sql("UPDATE organizations SET last_activity_at = :when WHERE id = :id")
                .param("when", java.sql.Timestamp.from(Instant.parse("2026-11-18T20:00:00Z")))
                .param("id", lyon.getId())
                .update();
        em.clear();
        assertThat(call(mvc, HttpMethod.GET, "/api/featured", null, null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"slug\": \"lyon-game-bar\", \"name\": \"Lyon Game Bar\"}");

        jdbc.sql("UPDATE organizations SET status = 'SUSPENDED' WHERE id = :id")
                .param("id", lyon.getId())
                .update();
        em.clear();
        assertThat(call(mvc, HttpMethod.GET, "/api/featured", null, null))
                .bodyJson()
                .isLenientlyEqualTo("{\"slug\": \"alsace-arena\"}");
    }
}
