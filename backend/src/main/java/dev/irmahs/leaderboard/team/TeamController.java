package dev.irmahs.leaderboard.team;

import dev.irmahs.leaderboard.members.MemberRole;
import dev.irmahs.leaderboard.team.TeamService.InviteInput;
import dev.irmahs.leaderboard.team.TeamService.NewInvite;
import dev.irmahs.leaderboard.team.TeamService.TeamView;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The admin Members screen. Admins of the organisation only. */
@RestController
@RequestMapping("/api/orgs/{slug}/admin")
@PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
public class TeamController {

    private final TeamService team;

    TeamController(TeamService team) {
        this.team = team;
    }

    @GetMapping("/members")
    public TeamView team(@PathVariable String slug) {
        return team.team(slug);
    }

    @PutMapping("/members/{userId}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeRole(@PathVariable String slug, @PathVariable UUID userId, @RequestBody RoleInput input) {
        team.changeRole(slug, userId, input.role());
    }

    @DeleteMapping("/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String slug, @PathVariable UUID userId) {
        team.remove(slug, userId);
    }

    @PostMapping("/members/{userId}/ownership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferOwnership(
            @PathVariable String slug, @PathVariable UUID userId, @AuthenticationPrincipal OidcUser owner) {
        team.transferOwnership(slug, owner.getSubject(), userId);
    }

    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public NewInvite invite(
            @PathVariable String slug, @RequestBody InviteInput input, @AuthenticationPrincipal OidcUser admin) {
        return team.invite(slug, admin.getSubject(), input);
    }

    @DeleteMapping("/invites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String slug, @PathVariable UUID id) {
        team.revoke(slug, id);
    }

    public record RoleInput(MemberRole role) {}
}
