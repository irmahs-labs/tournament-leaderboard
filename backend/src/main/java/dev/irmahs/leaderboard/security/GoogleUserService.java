package dev.irmahs.leaderboard.security;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/** Runs at each Google sign-in: refuses an unverified email, then records the account. */
@Component
class GoogleUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OAuth2UserService<OidcUserRequest, OidcUser> google;
    private final AccountService accounts;

    @Autowired
    GoogleUserService(AccountService accounts) {
        this(new OidcUserService(), accounts);
    }

    GoogleUserService(OAuth2UserService<OidcUserRequest, OidcUser> google, AccountService accounts) {
        this.google = google;
        this.accounts = accounts;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = google.loadUser(request);
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unverified_email", "Your Google email isn't verified", null));
        }
        accounts.signIn(new GoogleProfile(user.getSubject(), user.getEmail(), user.getFullName(), user.getPicture()));
        return user;
    }
}
