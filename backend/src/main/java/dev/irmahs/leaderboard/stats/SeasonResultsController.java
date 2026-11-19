package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.stats.SeasonResultsService.LeaderboardView;
import dev.irmahs.leaderboard.stats.SeasonResultsService.MatchCard;
import dev.irmahs.leaderboard.stats.SeasonResultsService.MatchView;
import dev.irmahs.leaderboard.stats.SeasonResultsService.PlayerSeasonView;
import dev.irmahs.leaderboard.stats.SeasonResultsService.SummaryView;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public result pages: Season leaderboard, Matches and Match detail. */
@RestController
@RequestMapping("/api/orgs/{slug}/seasons/{season}")
public class SeasonResultsController {

    private final SeasonResultsService results;

    SeasonResultsController(SeasonResultsService results) {
        this.results = results;
    }

    @GetMapping("/leaderboard")
    public LeaderboardView leaderboard(@PathVariable String slug, @PathVariable int season) {
        return results.leaderboard(slug, season);
    }

    @GetMapping("/summary")
    public SummaryView summary(@PathVariable String slug, @PathVariable int season) {
        return results.summary(slug, season);
    }

    @GetMapping("/matches")
    public List<MatchCard> matches(@PathVariable String slug, @PathVariable int season) {
        return results.matches(slug, season);
    }

    @GetMapping("/players/{player}")
    public PlayerSeasonView player(@PathVariable String slug, @PathVariable int season, @PathVariable UUID player) {
        return results.player(slug, season, player);
    }

    @GetMapping("/matches/{match}")
    public MatchView match(@PathVariable String slug, @PathVariable int season, @PathVariable int match) {
        return results.match(slug, season, match);
    }
}
