package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.LYON_ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.PLAYER;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.organizations.Organization;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** My account: leaving an organisation and deleting the account, on a real Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class AccountIT {

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

    Organization lyon;

    @BeforeEach
    void seed() {
        Organization alsace = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsace);
        em.persist(lyon);
        em.flush();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
        jdbc.sql("UPDATE organization_members SET is_owner = true WHERE organization_id = :id")
                .param("id", lyon.getId())
                .update();
        em.clear();
    }

    private long accountsWithEmail(String email) {
        return jdbc.sql("SELECT count(*) FROM app_users WHERE email = :email")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    @Test
    void aPlayerLeavesAnOrganisation() {
        assertThat(call(mvc, HttpMethod.DELETE, "/api/orgs/alsace-arena/membership", null, PLAYER))
                .hasStatus(204);
        em.flush();
        em.clear();
        assertThat(call(mvc, HttpMethod.GET, "/api/me", null, PLAYER))
                .bodyJson()
                .isLenientlyEqualTo("{\"memberships\": []}");
    }

    @Test
    void theOwnerCantLeaveAndSomeoneElseIsntAMember() {
        assertThat(call(mvc, HttpMethod.DELETE, "/api/orgs/lyon-game-bar/membership", null, LYON_ADMIN))
                .hasStatus(409);
        assertThat(call(mvc, HttpMethod.DELETE, "/api/orgs/alsace-arena/membership", null, NOBODY))
                .hasStatus(404);
        assertThat(call(mvc, HttpMethod.DELETE, "/api/orgs/alsace-arena/membership", null, null))
                .hasStatus(401);
    }

    @Test
    void deletingAnAccountRemovesItsMembershipsAndSignsOut() {
        UUID admin = accounts.user("google-admin").orElseThrow().getId();
        jdbc.sql("UPDATE organizations SET created_by = :admin WHERE slug = 'alsace-arena'")
                .param("admin", admin)
                .update();

        var deleted = call(mvc, HttpMethod.DELETE, "/api/me", null, ADMIN);

        assertThat(deleted).hasStatus(204);
        assertThat(deleted.getRequest().getSession(false) == null
                        || deleted.getRequest()
                                        .getSession(false)
                                        .getAttributeNames()
                                        .hasMoreElements()
                                == false)
                .isTrue();
        em.flush();
        assertThat(accountsWithEmail("admin@example.com")).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM organization_members WHERE user_id = :admin")
                        .param("admin", admin)
                        .query(Long.class)
                        .single())
                .isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM organizations WHERE slug = 'alsace-arena' AND created_by IS NULL")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void anOwnerCantDeleteTheirAccount() {
        assertThat(call(mvc, HttpMethod.DELETE, "/api/me", null, LYON_ADMIN)).hasStatus(409);
        assertThat(accountsWithEmail("lyon@example.com")).isEqualTo(1);
    }

    @Test
    void nobodySignedInCantDeleteAnything() {
        assertThat(call(mvc, HttpMethod.DELETE, "/api/me", null, null)).hasStatus(401);
    }
}
