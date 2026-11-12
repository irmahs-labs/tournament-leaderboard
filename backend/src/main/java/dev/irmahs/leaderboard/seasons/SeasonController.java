package dev.irmahs.leaderboard.seasons;

import dev.irmahs.leaderboard.seasons.SeasonService.RulesView;
import dev.irmahs.leaderboard.seasons.SeasonService.SeasonView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The season dropdown, and "How points are counted". */
@RestController
@RequestMapping("/api/orgs/{slug}/seasons")
public class SeasonController {

    private final SeasonService seasons;

    SeasonController(SeasonService seasons) {
        this.seasons = seasons;
    }

    @GetMapping
    public List<SeasonView> seasons(@PathVariable String slug) {
        return seasons.list(slug);
    }

    @GetMapping("/{number}/rules")
    public RulesView rules(@PathVariable String slug, @PathVariable int number) {
        return seasons.rules(slug, number);
    }
}
