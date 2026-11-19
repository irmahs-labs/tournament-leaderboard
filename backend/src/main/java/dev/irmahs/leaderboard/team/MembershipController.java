package dev.irmahs.leaderboard.team;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's own membership of an organisation, from My account. */
@RestController
public class MembershipController {

    private final TeamService team;

    MembershipController(TeamService team) {
        this.team = team;
    }

    @DeleteMapping("/api/orgs/{slug}/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable String slug, @AuthenticationPrincipal OidcUser member) {
        team.leave(slug, member.getSubject());
    }
}
