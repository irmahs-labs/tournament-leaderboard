package dev.irmahs.leaderboard;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.organizations.Organization;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The people the admin API tests sign in as: Alsace Arena's admin (from the test configuration's
 * initial members), one of its players, Lyon Game Bar's admin and someone of neither.
 */
final class AdminTestSupport {

    static final OidcLoginRequestPostProcessor ADMIN = as("google-admin");
    static final OidcLoginRequestPostProcessor PLAYER = as("google-player");
    static final OidcLoginRequestPostProcessor LYON_ADMIN = as("google-lyon");
    static final OidcLoginRequestPostProcessor NOBODY = as("google-nobody");

    private AdminTestSupport() {}

    static OidcLoginRequestPostProcessor as(String sub) {
        return oidcLogin().idToken(token -> token.subject(sub));
    }

    /** Signs the four people in and gives them their memberships. */
    static void signInEveryone(
            AccountService accounts, EntityManager em, JdbcClient jdbc, Organization alsace, Organization lyon) {
        accounts.signIn(new GoogleProfile("google-admin", "admin@example.com", "Admin"));
        AppUser player = accounts.signIn(new GoogleProfile("google-player", "player@example.com", "Player"));
        jdbc.sql("INSERT INTO organization_members (user_id, organization_id, role) VALUES (:user, :org, 'PLAYER')")
                .param("user", player.getId())
                .param("org", alsace.getId())
                .update();
        AppUser lyonAdmin = accounts.signIn(new GoogleProfile("google-lyon", "lyon@example.com", "Lyon"));
        em.persist(OrganizationMember.admin(lyonAdmin.getId(), lyon.getId()));
        accounts.signIn(new GoogleProfile("google-nobody", "nobody@example.com", "Nobody"));
        em.flush();
    }

    static MvcTestResult call(
            MockMvcTester mvc, HttpMethod method, String uri, String body, OidcLoginRequestPostProcessor who) {
        var request = mvc.method(method).uri(uri).with(csrf());
        if (who != null) {
            request = request.with(who);
        }
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    static MvcTestResult callWithoutCsrf(MockMvcTester mvc, HttpMethod method, String uri, String body) {
        var request = mvc.method(method).uri(uri).with(ADMIN);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }
}
