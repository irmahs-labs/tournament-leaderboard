package dev.irmahs.leaderboard.rosters;

import dev.irmahs.leaderboard.rosters.RosterService.Answer;
import dev.irmahs.leaderboard.rosters.RosterService.MyMatches;
import dev.irmahs.leaderboard.rosters.RosterService.PlanInput;
import dev.irmahs.leaderboard.rosters.RosterService.Roster;
import dev.irmahs.leaderboard.rosters.RosterService.Schedule;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The admins' Schedule and Match roster screens, and players answering for a match. */
@RestController
public class RosterController {

    private static final String ADMIN = "/api/orgs/{slug}/admin";

    private final RosterService rosters;

    RosterController(RosterService rosters) {
        this.rosters = rosters;
    }

    @GetMapping(ADMIN + "/seasons/{number}/schedule")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public Schedule schedule(@PathVariable String slug, @PathVariable int number) {
        return rosters.schedule(slug, number);
    }

    @PutMapping(ADMIN + "/seasons/{number}/schedule/{match}")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public Schedule plan(
            @PathVariable String slug,
            @PathVariable int number,
            @PathVariable int match,
            @RequestBody PlanInput input) {
        return rosters.plan(slug, number, match, input);
    }

    @GetMapping(ADMIN + "/matches/{match}/roster")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public Roster roster(@PathVariable String slug, @PathVariable int match) {
        return rosters.roster(slug, match);
    }

    @PutMapping(ADMIN + "/matches/{match}/roster/{player}")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public Roster setAnswer(
            @PathVariable String slug,
            @PathVariable int match,
            @PathVariable UUID player,
            @RequestBody StatusInput input,
            @AuthenticationPrincipal OidcUser admin) {
        if (input.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say where they go");
        }
        return rosters.setAnswer(slug, admin.getSubject(), match, player, input.status());
    }

    @DeleteMapping(ADMIN + "/matches/{match}/roster/{player}")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public Roster clearAnswer(
            @PathVariable String slug,
            @PathVariable int match,
            @PathVariable UUID player,
            @AuthenticationPrincipal OidcUser admin) {
        return rosters.setAnswer(slug, admin.getSubject(), match, player, null);
    }

    @PostMapping(ADMIN + "/matches/{match}/lock")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public Roster lock(@PathVariable String slug, @PathVariable int match) {
        return rosters.setLocked(slug, match, true);
    }

    @DeleteMapping(ADMIN + "/matches/{match}/lock")
    @PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
    public Roster unlock(@PathVariable String slug, @PathVariable int match) {
        return rosters.setLocked(slug, match, false);
    }

    @GetMapping("/api/orgs/{slug}/me/matches")
    public MyMatches mine(@PathVariable String slug, @AuthenticationPrincipal OidcUser user) {
        return rosters.mine(slug, user.getSubject());
    }

    @PutMapping("/api/orgs/{slug}/matches/{match}/attendance")
    public MyMatches answer(
            @PathVariable String slug,
            @PathVariable int match,
            @RequestBody AnswerInput input,
            @AuthenticationPrincipal OidcUser user) {
        return rosters.answer(slug, user.getSubject(), match, input.answer());
    }

    public record StatusInput(AttendanceStatus status) {}

    public record AnswerInput(Answer answer) {}
}
