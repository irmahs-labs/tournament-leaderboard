package dev.irmahs.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.organizations.Organization;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Google sign-in, accounts and /api/me on a real Postgres. Google itself is replaced by
 * Spring Security's test sign-in; src/test/resources/config/application.yml lists
 * platform@example.com as platform admin, owner@ and admin@example.com as alsace-arena's first
 * members, and admin@example.com for an organisation that doesn't exist. Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SignInIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AccountService accounts;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    UUID alsace;

    @BeforeEach
    void createAlsace() {
        Organization organization = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        em.persist(organization);
        alsace = organization.getId();
    }

    private static GoogleProfile profile(String sub, String email) {
        return new GoogleProfile(sub, email, "Name of " + sub, "https://example.com/" + sub + ".png");
    }

    @Test
    void theFirstSignInCreatesTheAccountAndTheNextOnesUpdateIt() {
        AppUser first = accounts.signIn(profile("google-1", "kes@example.com"));
        AppUser second = accounts.signIn(new GoogleProfile("google-1", "kestrel@example.com", "Kestrel", null));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getGoogleSub()).isEqualTo("google-1");
        assertThat(second.getEmail()).isEqualTo("kestrel@example.com");
        assertThat(second.getDisplayName()).isEqualTo("Kestrel");
        assertThat(second.getAvatarUrl()).isNull();
        assertThat(second.getCreatedAt()).isNotNull();
        assertThat(second.isPlatformAdmin()).isFalse();
        assertThat(count("app_users")).isEqualTo(1);
    }

    @Test
    void aListedEmailIsAPlatformAdmin() {
        assertThat(accounts.signIn(profile("google-1", "platform@example.com")).isPlatformAdmin())
                .isTrue();
    }

    @Test
    void theInitialMembersJoinTheirOrganisationAtTheirFirstSignIn() {
        AppUser owner = accounts.signIn(profile("google-1", "owner@example.com"));
        AppUser admin = accounts.signIn(profile("google-2", "Admin@Example.com"));
        accounts.signIn(profile("google-1", "owner@example.com"));

        em.flush();
        OrganizationMember ownership =
                em.find(OrganizationMember.class, new OrganizationMember.Key(owner.getId(), alsace));
        assertThat(ownership.isOwner()).isTrue();
        assertThat(ownership.getRole().name()).isEqualTo("ADMIN");
        assertThat(ownership.getPlayerId()).isNull();
        assertThat(ownership.getJoinedAt()).isNotNull();
        assertThat(ownership.getUserId()).isEqualTo(owner.getId());
        assertThat(ownership.getOrganizationId()).isEqualTo(alsace);
        OrganizationMember adminship =
                em.find(OrganizationMember.class, new OrganizationMember.Key(admin.getId(), alsace));
        assertThat(adminship.isOwner()).isFalse();
        assertThat(adminship.getRole().name()).isEqualTo("ADMIN");
        // Signing in again adds nothing, and the organisation that doesn't exist is skipped.
        assertThat(count("organization_members")).isEqualTo(2);
    }

    @Test
    void anInitialOwnerBecomesAnAdminWhenTheOrganisationAlreadyHasAnOwner() {
        AppUser someone = accounts.signIn(profile("google-9", "someone@example.com"));
        em.persist(OrganizationMember.owner(someone.getId(), alsace));
        em.flush();

        AppUser owner = accounts.signIn(profile("google-1", "owner@example.com"));
        em.flush();

        OrganizationMember membership =
                em.find(OrganizationMember.class, new OrganizationMember.Key(owner.getId(), alsace));
        assertThat(membership.isOwner()).isFalse();
        assertThat(membership.getRole().name()).isEqualTo("ADMIN");
    }

    @Test
    void someoneSignedOutGetsUnauthorised() {
        assertThat(mvc.get().uri("/api/me")).hasStatus(401);
    }

    @Test
    void tellsTheSignedInPersonWhoTheyAreAndWhereTheyBelong() {
        accounts.signIn(profile("google-1", "owner@example.com"));
        Organization closed = new Organization("closed-bar", "Closed Bar", null);
        em.persist(closed);
        em.flush();
        UUID ownerId = jdbc.sql("SELECT id FROM app_users WHERE google_sub = 'google-1'")
                .query(UUID.class)
                .single();
        em.persist(OrganizationMember.admin(ownerId, closed.getId()));
        em.flush();
        jdbc.sql("UPDATE organizations SET status = 'SUSPENDED' WHERE slug = 'closed-bar'")
                .update();

        assertThat(mvc.get().uri("/api/me").with(oidcLogin().idToken(token -> token.subject("google-1"))))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"email": "owner@example.com", "displayName": "Name of google-1",
                         "avatarUrl": "https://example.com/google-1.png", "platformAdmin": false,
                         "memberships": [{"slug": "alsace-arena", "name": "Alsace Arena", "role": "ADMIN", "owner": true}]}""");
    }

    @Test
    void aSessionWhoseAccountNoLongerExistsMustSignInAgain() {
        assertThat(mvc.get().uri("/api/me").with(oidcLogin().idToken(token -> token.subject("google-gone"))))
                .hasStatus(401);
    }

    @Test
    void signingInStartsAtGoogleWithTheAppsOwnClient() {
        MvcTestResult result = mvc.get().uri("/oauth2/authorization/google").exchange();

        assertThat(result).hasStatus(302);
        assertThat(result.getResponse().getHeader("Location"))
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                .contains("client_id=test-client-id")
                .contains("scope=openid%20email%20profile")
                .contains("redirect_uri=http://localhost/login/oauth2/code/google");
    }

    @Test
    void signingOutNeedsTheCsrfToken() {
        assertThat(mvc.post().uri("/api/logout").with(oidcLogin())).hasStatus(403);
        assertThat(mvc.post().uri("/api/logout").with(oidcLogin()).with(csrf())).hasStatus(204);
    }

    @Test
    void theResultPagesStayPublicAndTheRestOfTheApiDoesNot() {
        assertThat(mvc.get().uri("/api/orgs/alsace-arena")).hasStatusOk();
        assertThat(mvc.get().uri("/api/version")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
        assertThat(mvc.get().uri("/api/orgs-admin")).hasStatus(401);
        for (String address : List.of("/api/me", "/api/anything")) {
            assertThat(mvc.get().uri(address)).hasStatus(401);
        }
    }

    private long count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
