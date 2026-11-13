package dev.irmahs.leaderboard.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class GoogleUserServiceTest {

    @SuppressWarnings("unchecked")
    private final OAuth2UserService<OidcUserRequest, OidcUser> google = mock(OAuth2UserService.class);

    private final AccountService accounts = mock(AccountService.class);
    private final GoogleUserService service = new GoogleUserService(google, accounts);
    private final OidcUserRequest request = mock(OidcUserRequest.class);

    private static OidcUser googleUser(Boolean emailVerified) {
        Map<String, Object> claims = new HashMap<>(Map.of(
                "sub", "google-123",
                "email", "kes@example.com",
                "name", "Kestrel Martin",
                "picture", "https://example.com/kes.png"));
        if (emailVerified != null) {
            claims.put("email_verified", emailVerified);
        }
        OidcIdToken token =
                new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new DefaultOidcUser(List.of(), token);
    }

    @Test
    void recordsTheAccountOfAVerifiedEmail() {
        OidcUser user = googleUser(true);
        when(google.loadUser(request)).thenReturn(user);

        assertThat(service.loadUser(request)).isSameAs(user);
        verify(accounts)
                .signIn(new GoogleProfile(
                        "google-123", "kes@example.com", "Kestrel Martin", "https://example.com/kes.png"));
    }

    @Test
    void refusesAnEmailGoogleHasNotVerified() {
        when(google.loadUser(request)).thenReturn(googleUser(false));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("isn't verified");
        verify(accounts, never()).signIn(any());
    }

    @Test
    void refusesAnAccountThatDoesNotSayItsEmailIsVerified() {
        when(google.loadUser(request)).thenReturn(googleUser(null));

        assertThatThrownBy(() -> service.loadUser(request)).isInstanceOf(OAuth2AuthenticationException.class);
        verify(accounts, never()).signIn(any());
    }

    @Test
    void canBeBuiltWithGooglesOwnUserService() {
        assertThat(new GoogleUserService(accounts)).isNotNull();
    }
}
