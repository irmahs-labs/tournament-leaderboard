package dev.irmahs.leaderboard;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * A Google sign-in saved in the database the way the app saves one, with the cookie a browser
 * would send back, unlike Spring Security's test sign-in, which never reaches the session store.
 */
final class StoredSignIn {

    static final String CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

    private StoredSignIn() {}

    static SecurityContext context(String sub) {
        Instant now = Instant.now();
        OidcIdToken token =
                new OidcIdToken("token", now, now.plusSeconds(3600), Map.of("sub", sub, "email", sub + "@example.com"));
        DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token);
        return new SecurityContextImpl(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
    }

    /** Saves a session signed in as this Google account, and returns its id. */
    static <S extends Session> String save(SessionRepository<S> sessions, String sub) {
        S session = sessions.createSession();
        session.setAttribute(CONTEXT, context(sub));
        sessions.save(session);
        return session.getId();
    }

    /** The session cookie for this id, encoded as Spring Session writes it. */
    static Cookie cookie(String sessionId) {
        return new Cookie("JSESSIONID", Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8)));
    }
}
