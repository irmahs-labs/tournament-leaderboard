package dev.irmahs.leaderboard.accounts;

import dev.irmahs.leaderboard.accounts.AccountService.Me;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Who is signed in, and which organisations they belong to. */
@RestController
public class MeController {

    private final AccountService accounts;

    MeController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/api/me")
    public Me me(@AuthenticationPrincipal OidcUser user) {
        return accounts.me(user.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again"));
    }

    /** Deletes the signed-in person's account, then signs them out. */
    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal OidcUser user, HttpServletRequest request) throws ServletException {
        accounts.delete(user.getSubject());
        request.logout();
    }
}
