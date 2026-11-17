package dev.irmahs.leaderboard.security;

import dev.irmahs.leaderboard.accounts.AccountService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Who may do what in an organisation, for {@code @PreAuthorize}:
 * {@code @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")}.
 */
@Component("orgAccess")
public class OrgAccess {

    private final AccountService accounts;

    OrgAccess(AccountService accounts) {
        this.accounts = accounts;
    }

    /** Whether the signed-in person is an admin of the active organisation with this slug. */
    public boolean isAdmin(String slug, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof OidcUser user
                && accounts.isAdmin(user.getSubject(), slug);
    }
}
