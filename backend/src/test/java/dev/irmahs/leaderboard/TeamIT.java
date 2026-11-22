package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.LYON_ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.PLAYER;
import static dev.irmahs.leaderboard.AdminTestSupport.as;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static dev.irmahs.leaderboard.AdminTestSupport.callWithoutCsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import dev.irmahs.leaderboard.organizations.Organization;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Members and invites on a real Postgres. Alsace Arena has its owner, an admin and a player; Lyon
 * Game Bar has its own admin. Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class TeamIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String ADMIN_API = "/api/orgs/alsace-arena/admin";
    static final OidcLoginRequestPostProcessor OWNER = as("google-owner");
    static final OidcLoginRequestPostProcessor GUEST = as("google-guest");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountService accounts;

    UUID owner;
    UUID admin;
    UUID player;

    @BeforeEach
    void seed() {
        Organization alsace = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        Organization lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsace);
        em.persist(lyon);
        em.flush();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
        owner = accounts.signIn(new GoogleProfile("google-owner", "owner@example.com", "Thomas"))
                .getId();
        accounts.signIn(new GoogleProfile("google-guest", "Guest@Example.com", "Guest"));
        admin = accounts.user("google-admin").orElseThrow().getId();
        player = accounts.user("google-player").orElseThrow().getId();
        em.flush();
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, OidcLoginRequestPostProcessor who) {
        return call(mvc, method, uri, body, who);
    }

    /** A request to the join API from its own address, so the lookup limit never mixes tests. */
    private MvcTestResult join(HttpMethod method, String uri, OidcLoginRequestPostProcessor who) {
        var request = mvc.method(method).uri(uri).with(csrf()).with(fresh -> {
            fresh.setRemoteAddr("10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
            return fresh;
        });
        if (who != null) {
            request = request.with(who);
        }
        return request.exchange();
    }

    /** Invites this email as the admin, and writes it to the database; answers the code. */
    private String invite(String email) {
        byte[] answer = send(HttpMethod.POST, ADMIN_API + "/invites", inviteBody(email, 60), ADMIN)
                .getResponse()
                .getContentAsByteArray();
        em.flush();
        return JsonPath.parse(new String(answer, StandardCharsets.UTF_8)).read("$.code");
    }

    private static String inviteBody(String email, int minutes) {
        return "{\"email\": \"" + email + "\", \"expiresInMinutes\": " + minutes + "}";
    }

    static Stream<Arguments> adminRoutes() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, ADMIN_API + "/members", null),
                Arguments.of(HttpMethod.POST, ADMIN_API + "/invites", inviteBody("new@example.com", 30)));
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void theMembersScreenIsForTheOrganisationsAdminsOnly(HttpMethod method, String uri, String body) {
        assertThat(send(method, uri, body, null)).hasStatus(401);
        assertThat(send(method, uri, body, NOBODY)).hasStatus(403);
        assertThat(send(method, uri, body, PLAYER)).hasStatus(403);
        assertThat(send(method, uri, body, LYON_ADMIN)).hasStatus(403);
        assertThat(send(method, uri, body, ADMIN)).hasStatus2xxSuccessful();
    }

    @Test
    void changesNeedTheCsrfToken() {
        assertThat(callWithoutCsrf(mvc, HttpMethod.POST, ADMIN_API + "/invites", inviteBody("a@b.co", 30)))
                .hasStatus(403);
    }

    @Test
    void listsTheMembersAndTheWaitingInvites() {
        invite("guest@example.com");

        MvcTestResult team = send(HttpMethod.GET, ADMIN_API + "/members", null, ADMIN);

        assertThat(team)
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.members[?(@.owner == true)].email")
                .asArray()
                .containsExactly("owner@example.com");
        assertThat(team)
                .bodyJson()
                .extractingPath("$.members[*].role")
                .asArray()
                .containsExactlyInAnyOrder("ADMIN", "PLAYER", "ADMIN");
        assertThat(team)
                .bodyJson()
                .isLenientlyEqualTo("{\"invites\": [{\"email\": \"guest@example.com\", \"invitedBy\": \"Admin\"}]}");
    }

    @Test
    void anInviteMakesItsGoogleAccountAnAdminOnce() {
        String code = invite("guest@example.com");

        assertThat(code).matches("[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}");
        assertThat(join(HttpMethod.GET, "/api/invites/" + code.toLowerCase().replace("-", ""), null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"organization": {"slug": "alsace-arena", "name": "Alsace Arena"},
                         "role": "ADMIN", "email": "guest@example.com", "invitedBy": "Admin", "state": "VALID"}""");
        assertThat(join(HttpMethod.POST, "/api/invites/" + code + "/accept", GUEST))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"slug\": \"alsace-arena\", \"role\": \"ADMIN\", \"owner\": false}");
        assertThat(join(HttpMethod.POST, "/api/invites/" + code + "/accept", GUEST))
                .hasStatus(410);
        assertThat(join(HttpMethod.GET, "/api/invites/" + code, null))
                .bodyJson()
                .extractingPath("$.state")
                .isEqualTo("USED");
    }

    @Test
    void anInviteDoesNothingForAnotherAccount() {
        String code = invite("guest@example.com");

        assertThat(join(HttpMethod.POST, "/api/invites/" + code + "/accept", NOBODY))
                .hasStatus(403);
        assertThat(join(HttpMethod.POST, "/api/invites/" + code + "/accept", null))
                .hasStatus(401);
        assertThat(join(HttpMethod.POST, "/api/invites/" + code + "/accept", GUEST))
                .hasStatusOk();
    }

    @Test
    void aPlayerInvitedBecomesAnAdmin() {
        String code = invite("player@example.com");

        assertThat(join(HttpMethod.POST, "/api/invites/" + code + "/accept", PLAYER))
                .bodyJson()
                .extractingPath("$.role")
                .isEqualTo("ADMIN");
    }

    @Test
    void anExpiredOrRevokedInviteNoLongerWorks() {
        String expired = invite("guest@example.com");
        jdbc.sql("UPDATE organization_invites SET expires_at = now() - interval '1 minute'")
                .update();
        em.clear(); // the update went past Hibernate: forget the invite as it was
        String revoked = invite("guest@example.com");
        UUID revokedId = jdbc.sql("SELECT id FROM organization_invites WHERE expires_at > now()")
                .query(UUID.class)
                .single();

        assertThat(send(HttpMethod.DELETE, ADMIN_API + "/invites/" + revokedId, null, ADMIN))
                .hasStatus(204);
        assertThat(join(HttpMethod.POST, "/api/invites/" + expired + "/accept", GUEST))
                .hasStatus(410);
        assertThat(join(HttpMethod.POST, "/api/invites/" + revoked + "/accept", GUEST))
                .hasStatus(410);
        assertThat(join(HttpMethod.GET, "/api/invites/" + revoked, null))
                .bodyJson()
                .extractingPath("$.state")
                .isEqualTo("REVOKED");
    }

    @Test
    void aCodeThatDoesNotExistIsNotFound() {
        assertThat(join(HttpMethod.GET, "/api/invites/AAAA-BBBB", null)).hasStatus(404);
        assertThat(join(HttpMethod.GET, "/api/invites/not-a-code", null)).hasStatus(404);
    }

    @Test
    void lookupsAreLimitedPerAddress() {
        var request = mvc.get().uri("/api/invites/AAAA-BBBB").with(fresh -> {
            fresh.setRemoteAddr("192.0.2.77");
            return fresh;
        });
        for (int i = 0; i < 10; i++) {
            assertThat(request.exchange()).hasStatus(404);
        }

        assertThat(request.exchange()).hasStatus(429);
    }

    @Test
    void refusesAWrongInvite() {
        assertThat(send(HttpMethod.POST, ADMIN_API + "/invites", inviteBody("not an email", 60), ADMIN))
                .hasStatus(400);
        assertThat(send(HttpMethod.POST, ADMIN_API + "/invites", inviteBody("guest@example.com", 45), ADMIN))
                .hasStatus(400);
        assertThat(send(HttpMethod.POST, ADMIN_API + "/invites", inviteBody("ADMIN@example.com", 60), ADMIN))
                .hasStatus(409);
    }

    @Test
    void anAdminChangesRolesAndRemovesMembersButNeverTheOwner() {
        assertThat(send(HttpMethod.PUT, ADMIN_API + "/members/" + player + "/role", "{\"role\": \"ADMIN\"}", ADMIN))
                .hasStatus(204);
        assertThat(send(HttpMethod.PUT, ADMIN_API + "/members/" + owner + "/role", "{\"role\": \"PLAYER\"}", ADMIN))
                .hasStatus(409);
        assertThat(send(HttpMethod.DELETE, ADMIN_API + "/members/" + owner, null, ADMIN))
                .hasStatus(409);
        assertThat(send(HttpMethod.DELETE, ADMIN_API + "/members/" + player, null, ADMIN))
                .hasStatus(204);
        assertThat(send(HttpMethod.GET, ADMIN_API + "/members", null, ADMIN))
                .bodyJson()
                .extractingPath("$.members")
                .asArray()
                .hasSize(2);
    }

    @Test
    void onlyTheOwnerHandsOwnershipToAnotherAdmin() {
        String toAdmin = ADMIN_API + "/members/" + admin + "/ownership";

        assertThat(send(HttpMethod.POST, toAdmin, null, ADMIN)).hasStatus(403);
        assertThat(send(HttpMethod.POST, ADMIN_API + "/members/" + player + "/ownership", null, OWNER))
                .hasStatus(409);
        assertThat(send(HttpMethod.POST, toAdmin, null, OWNER)).hasStatus(204);
        assertThat(send(HttpMethod.GET, ADMIN_API + "/members", null, ADMIN))
                .bodyJson()
                .extractingPath("$.members[?(@.owner == true)].email")
                .asArray()
                .containsExactly("admin@example.com");
    }

    @Test
    void thePlatformAdminRunsAnOrganisationAndGivesOneWithoutAnOwnerItsOwner() {
        accounts.signIn(new GoogleProfile("google-platform", "platform@example.com", "Irma"));
        OidcLoginRequestPostProcessor platform = as("google-platform");
        jdbc.sql("UPDATE organization_members SET is_owner = false WHERE user_id = :owner")
                .param("owner", owner)
                .update();
        em.clear();
        String toAdmin = ADMIN_API + "/members/" + admin + "/ownership";

        assertThat(send(HttpMethod.GET, ADMIN_API + "/members", null, platform))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.members[?(@.owner == true)]")
                .asArray()
                .isEmpty();
        assertThat(send(HttpMethod.POST, toAdmin, null, OWNER)).hasStatus(403);
        assertThat(send(HttpMethod.POST, toAdmin, null, platform)).hasStatus(204);
        assertThat(send(HttpMethod.GET, ADMIN_API + "/members", null, platform))
                .bodyJson()
                .extractingPath("$.members[?(@.owner == true)].email")
                .asArray()
                .containsExactly("admin@example.com");
    }
}
