package dev.irmahs.leaderboard.accounts;

import dev.irmahs.leaderboard.accounts.AccountService.Me;
import dev.irmahs.leaderboard.accounts.IdentityService.IdentityInput;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Who is signed in, and which organisations they belong to. */
@RestController
public class MeController {

    private final AccountService accounts;
    private final IdentityService identity;

    MeController(AccountService accounts, IdentityService identity) {
        this.accounts = accounts;
        this.identity = identity;
    }

    @GetMapping("/api/me")
    public Me me(@AuthenticationPrincipal OidcUser user) {
        return accounts.me(user.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again"));
    }

    /** Changes the Riot ID and Discord username shown on every profile linked to the account. */
    @PutMapping("/api/me/identity")
    public Me changeIdentity(@AuthenticationPrincipal OidcUser user, @RequestBody IdentityInput input) {
        identity.change(user.getSubject(), input);
        return me(user);
    }

    /** Deletes the signed-in person's account, then signs them out. */
    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal OidcUser user, HttpServletRequest request) throws ServletException {
        accounts.delete(user.getSubject());
        request.logout();
    }
}
