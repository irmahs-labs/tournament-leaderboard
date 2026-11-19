package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.stats.ScoresService.CheckedLine;
import dev.irmahs.leaderboard.stats.ScoresService.GameScores;
import dev.irmahs.leaderboard.stats.ScoresService.GameScoresInput;
import dev.irmahs.leaderboard.stats.ScoresService.PreviousRoster;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The Enter scores screen. Admins of the organisation only. */
@RestController
@RequestMapping("/api/orgs/{slug}/admin/seasons/{season}")
@PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
public class ScoresController {

    private static final String GAME = "/matches/{match}/games/{game}";

    private final ScoresService scores;

    ScoresController(ScoresService scores) {
        this.scores = scores;
    }

    @GetMapping(GAME)
    public GameScores load(
            @PathVariable String slug, @PathVariable int season, @PathVariable int match, @PathVariable int game) {
        return scores.load(slug, season, match, game);
    }

    @PutMapping(GAME)
    public GameScores save(
            @PathVariable String slug,
            @PathVariable int season,
            @PathVariable int match,
            @PathVariable int game,
            @RequestBody GameScoresInput input) {
        return scores.save(slug, season, match, game, input);
    }

    @DeleteMapping(GAME)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(
            @PathVariable String slug, @PathVariable int season, @PathVariable int match, @PathVariable int game) {
        scores.clear(slug, season, match, game);
    }

    @GetMapping(GAME + "/previous-roster")
    public PreviousRoster previousRoster(
            @PathVariable String slug, @PathVariable int season, @PathVariable int match, @PathVariable int game) {
        return scores.previousRoster(slug, season, match, game);
    }

    /**
     * The "Check bonuses" button: the automatic bonuses each line would win.
     *
     * @param lines each line's stats and whether the player won, in the form's order
     * @return each line's bonus codes, in the same order
     */
    @PostMapping("/bonus-check")
    public List<List<String>> checkBonuses(
            @PathVariable String slug, @PathVariable int season, @RequestBody List<CheckedLine> lines) {
        return scores.checkBonuses(slug, season, lines);
    }
}
