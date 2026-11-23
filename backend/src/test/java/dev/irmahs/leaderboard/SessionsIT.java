package dev.irmahs.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Sign-in sessions kept in the database, so they outlive a deploy. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SessionsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    SessionRepository<? extends Session> sessions;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AccountService accounts;

    @AfterEach
    void forgetSessionsAndAccounts() {
        jdbc.sql("DELETE FROM spring_session").update();
        jdbc.sql("DELETE FROM app_users WHERE google_sub LIKE 'sessions-%'").update();
    }

    @Test
    void sessionsAreTheDatabaseOnes() {
        assertThat(sessions).isInstanceOf(JdbcIndexedSessionRepository.class);
    }

    @Test
    void aSignInIsKeptInTheDatabaseAndReadBack() {
        String id = StoredSignIn.save(sessions, "sessions-1");

        assertThat(jdbc.sql("SELECT count(*) FROM spring_session")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
        SecurityContext context = sessions.findById(id).getAttribute(StoredSignIn.CONTEXT);
        assertThat(context.getAuthentication().getName()).isEqualTo("sessions-1");
        assertThat(sessions.findById(id).getMaxInactiveInterval()).hasDays(30);
    }

    @Test
    void theSessionCookieSignsInAndSigningOutDeletesTheSession() {
        accounts.signIn(new GoogleProfile("sessions-2", "sessions-2@example.com", "Wren"));
        String id = StoredSignIn.save(sessions, "sessions-2");

        assertThat(mvc.get().uri("/api/me").cookie(StoredSignIn.cookie(id)))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.email")
                .isEqualTo("sessions-2@example.com");
        assertThat(mvc.post().uri("/api/logout").with(csrf()).cookie(StoredSignIn.cookie(id)))
                .hasStatus(204);

        assertThat(sessions.findById(id)).isNull();
        assertThat(mvc.get().uri("/api/me").cookie(StoredSignIn.cookie(id))).hasStatus(401);
    }

    @Test
    void aSessionThisVersionCantReadSignsOutInsteadOfFailing() {
        accounts.signIn(new GoogleProfile("sessions-3", "sessions-3@example.com", "Moth"));
        String id = StoredSignIn.save(sessions, "sessions-3");
        jdbc.sql("UPDATE spring_session_attributes SET attribute_bytes = :bytes")
                .param("bytes", new byte[] {(byte) 0xAC, (byte) 0xED, 0, 5, 's', 'r', 0, 9})
                .update();

        Session session = sessions.findById(id);

        assertThat(session).isNotNull();
        assertThat((Object) session.getAttribute(StoredSignIn.CONTEXT)).isNull();
        assertThat(mvc.get().uri("/api/me").cookie(StoredSignIn.cookie(id))).hasStatus(401);
    }

    @Test
    void theSessionCookieOutlivesTheBrowserForThirtyDays() {
        // Starting a Google sign-in keeps where it's going in a new session, which sets the cookie.
        assertThat(mvc.get().uri("/oauth2/authorization/google"))
                .cookies()
                .hasCookieSatisfying("JSESSIONID", cookie -> {
                    assertThat(cookie.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
                    assertThat(cookie.isHttpOnly()).isTrue();
                });
    }
}
