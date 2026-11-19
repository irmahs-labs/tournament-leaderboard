package dev.irmahs.leaderboard.platform;

import dev.irmahs.leaderboard.organizations.OrganizationStatus;
import dev.irmahs.leaderboard.platform.PlatformService.PlatformOrganization;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The platform admin's Organisations screen. */
@RestController
@RequestMapping("/api/platform/organizations")
public class PlatformController {

    private final PlatformService platform;

    PlatformController(PlatformService platform) {
        this.platform = platform;
    }

    @GetMapping
    public List<PlatformOrganization> organizations(@AuthenticationPrincipal OidcUser admin) {
        return platform.organizations(admin.getSubject());
    }

    @PutMapping("/{slug}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeStatus(
            @PathVariable String slug, @RequestBody StatusInput input, @AuthenticationPrincipal OidcUser admin) {
        platform.changeStatus(admin.getSubject(), slug, input.status());
    }

    public record StatusInput(OrganizationStatus status) {}
}
