package dev.irmahs.leaderboard.claims;

import dev.irmahs.leaderboard.claims.ClaimService.ClaimRow;
import dev.irmahs.leaderboard.claims.ClaimService.ClaimState;
import dev.irmahs.leaderboard.claims.ClaimService.NewClaim;
import dev.irmahs.leaderboard.claims.ClaimService.PlayerSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Joining as a player, claiming a profile, and the admins' Claims queue. */
@RestController
public class ClaimController {

    private final ClaimService claims;

    ClaimController(ClaimService claims) {
        this.claims = claims;
    }

    @PostMapping("/api/orgs/{slug}/membership")
    public ClaimState join(@PathVariable String slug, @AuthenticationPrincipal OidcUser user) {
        return claims.join(slug, user.getSubject());
    }

    @GetMapping("/api/orgs/{slug}/claim")
    public ClaimState mine(@PathVariable String slug, @AuthenticationPrincipal OidcUser user) {
        return claims.mine(slug, user.getSubject());
    }

    @GetMapping("/api/orgs/{slug}/claim/players")
    public List<PlayerSummary> unclaimed(@PathVariable String slug, @AuthenticationPrincipal OidcUser user) {
        return claims.unclaimed(slug, user.getSubject());
    }

    @PostMapping("/api/orgs/{slug}/claim")
    public ClaimState claim(
            @PathVariable String slug, @RequestBody NewClaim input, @AuthenticationPrincipal OidcUser user) {
        return claims.claim(slug, user.getSubject(), input);
    }

    @DeleteMapping("/api/orgs/{slug}/claim")
    public ClaimState cancel(@PathVariable String slug, @AuthenticationPrincipal OidcUser user) {
        return claims.cancel(slug, user.getSubject());
    }

    @GetMapping("/api/orgs/{slug}/admin/claims")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public List<ClaimRow> queue(
            @PathVariable String slug,
            @RequestParam(defaultValue = "PENDING") ClaimStatus status,
            @AuthenticationPrincipal OidcUser admin) {
        return claims.queue(slug, admin.getSubject(), status);
    }

    @PostMapping("/api/orgs/{slug}/admin/claims/{id}/accept")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public ClaimRow accept(@PathVariable String slug, @PathVariable UUID id, @AuthenticationPrincipal OidcUser admin) {
        return claims.accept(slug, admin.getSubject(), id);
    }

    @PostMapping("/api/orgs/{slug}/admin/claims/{id}/decline")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public ClaimRow decline(
            @PathVariable String slug,
            @PathVariable UUID id,
            @RequestBody Decline input,
            @AuthenticationPrincipal OidcUser admin) {
        return claims.decline(slug, admin.getSubject(), id, input.reason());
    }

    public record Decline(String reason) {}
}
