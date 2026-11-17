package dev.irmahs.leaderboard.players;

import dev.irmahs.leaderboard.players.PlayerAdminService.PlayerInput;
import dev.irmahs.leaderboard.players.PlayerAdminService.PlayerView;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The admin Players screen. Admins of the organisation only. */
@RestController
@RequestMapping("/api/orgs/{slug}/admin/players")
@PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
public class PlayerAdminController {

    private final PlayerAdminService players;

    PlayerAdminController(PlayerAdminService players) {
        this.players = players;
    }

    @GetMapping
    public List<PlayerView> search(@PathVariable String slug, @RequestParam(required = false) String q) {
        return players.search(slug, q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerView add(@PathVariable String slug, @RequestBody PlayerInput input) {
        return players.add(slug, input);
    }

    @PutMapping("/{id}")
    public PlayerView edit(@PathVariable String slug, @PathVariable UUID id, @RequestBody PlayerInput input) {
        return players.edit(slug, id, input);
    }
}
