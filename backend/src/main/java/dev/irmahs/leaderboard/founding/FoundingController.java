package dev.irmahs.leaderboard.founding;

import dev.irmahs.leaderboard.founding.FoundingService.Founded;
import dev.irmahs.leaderboard.founding.FoundingService.Founding;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Creating an organisation, from the home page. Signed in only. */
@RestController
public class FoundingController {

    private final FoundingService founding;

    FoundingController(FoundingService founding) {
        this.founding = founding;
    }

    @PostMapping("/api/orgs")
    @ResponseStatus(HttpStatus.CREATED)
    public Founded found(@RequestBody Founding input, @AuthenticationPrincipal OidcUser founder) {
        return founding.found(founder.getSubject(), input);
    }
}
