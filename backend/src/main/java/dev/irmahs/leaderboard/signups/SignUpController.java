package dev.irmahs.leaderboard.signups;

import dev.irmahs.leaderboard.signups.SignUpService.MySeasons;
import dev.irmahs.leaderboard.signups.SignUpService.SignUpSettings;
import dev.irmahs.leaderboard.signups.SignUpService.SignUps;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Signing up for a season, and the admins' Season sign-ups screen. */
@RestController
public class SignUpController {

    private final SignUpService signUps;

    SignUpController(SignUpService signUps) {
        this.signUps = signUps;
    }

    @GetMapping("/api/orgs/{slug}/me/seasons")
    public MySeasons mine(@PathVariable String slug, @AuthenticationPrincipal OidcUser user) {
        return signUps.mine(slug, user.getSubject());
    }

    @PostMapping("/api/orgs/{slug}/seasons/{number}/registration")
    public MySeasons signUp(
            @PathVariable String slug, @PathVariable int number, @AuthenticationPrincipal OidcUser user) {
        return signUps.signUp(slug, user.getSubject(), number);
    }

    @DeleteMapping("/api/orgs/{slug}/seasons/{number}/registration")
    public MySeasons withdraw(
            @PathVariable String slug, @PathVariable int number, @AuthenticationPrincipal OidcUser user) {
        return signUps.withdraw(slug, user.getSubject(), number);
    }

    @GetMapping("/api/orgs/{slug}/admin/seasons/{number}/registrations")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public SignUps list(@PathVariable String slug, @PathVariable int number) {
        return signUps.signUps(slug, number);
    }

    @PutMapping("/api/orgs/{slug}/admin/seasons/{number}/signup")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public SignUps settings(@PathVariable String slug, @PathVariable int number, @RequestBody SignUpSettings input) {
        return signUps.setSignUps(slug, number, input);
    }

    @PostMapping("/api/orgs/{slug}/admin/seasons/{number}/registrations/{player}")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public SignUps add(
            @PathVariable String slug,
            @PathVariable int number,
            @PathVariable UUID player,
            @AuthenticationPrincipal OidcUser admin) {
        return signUps.add(slug, admin.getSubject(), number, player);
    }

    @DeleteMapping("/api/orgs/{slug}/admin/seasons/{number}/registrations/{player}")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public SignUps remove(@PathVariable String slug, @PathVariable int number, @PathVariable UUID player) {
        return signUps.remove(slug, number, player);
    }
}
