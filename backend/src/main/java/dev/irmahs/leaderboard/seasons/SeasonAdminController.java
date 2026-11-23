package dev.irmahs.leaderboard.seasons;

import dev.irmahs.leaderboard.seasons.SeasonAdminService.NewSeason;
import dev.irmahs.leaderboard.seasons.SeasonAdminService.SeasonInput;
import dev.irmahs.leaderboard.seasons.SeasonService.SeasonView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The admin Seasons screen. Admins of the organisation only. */
@RestController
@RequestMapping("/api/orgs/{slug}/admin/seasons")
@PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
public class SeasonAdminController {

    private final SeasonAdminService seasons;

    SeasonAdminController(SeasonAdminService seasons) {
        this.seasons = seasons;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeasonView start(@PathVariable String slug, @RequestBody NewSeason input) {
        return seasons.start(slug, input);
    }

    @PutMapping("/{number}")
    public SeasonView edit(@PathVariable String slug, @PathVariable int number, @RequestBody SeasonInput input) {
        return seasons.edit(slug, number, input);
    }
}
