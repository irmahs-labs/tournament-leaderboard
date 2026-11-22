package dev.irmahs.leaderboard.notices;

import dev.irmahs.leaderboard.notices.NoticeService.Notices;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's notices, across every organisation they belong to. */
@RestController
public class NoticeController {

    private final NoticeService notices;

    NoticeController(NoticeService notices) {
        this.notices = notices;
    }

    @GetMapping("/api/me/notifications")
    public Notices mine(@AuthenticationPrincipal OidcUser user) {
        return notices.mine(user.getSubject());
    }

    @PostMapping("/api/me/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal OidcUser user) {
        notices.read(user.getSubject());
    }
}
