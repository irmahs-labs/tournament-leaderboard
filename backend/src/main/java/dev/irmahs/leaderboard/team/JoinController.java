package dev.irmahs.leaderboard.team;

import dev.irmahs.leaderboard.members.Membership;
import dev.irmahs.leaderboard.team.JoinService.JoinView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The join page: anyone can see what a code is for; using it needs a signed-in account. Lookups
 * are limited per address, so guessing codes is pointless.
 */
@RestController
public class JoinController {

    private final JoinService join;
    private final CodeLookupLimit limit;

    JoinController(JoinService join, CodeLookupLimit limit) {
        this.join = join;
        this.limit = limit;
    }

    @GetMapping("/api/invites/{code}")
    public JoinView look(@PathVariable String code, HttpServletRequest request) {
        checkLimit(request);
        return join.look(code);
    }

    @PostMapping("/api/invites/{code}/accept")
    public Membership accept(
            @PathVariable String code, @AuthenticationPrincipal OidcUser user, HttpServletRequest request) {
        checkLimit(request);
        return join.accept(code, user.getSubject());
    }

    private void checkLimit(HttpServletRequest request) {
        if (!limit.allow(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many tries: wait a minute");
        }
    }
}
