package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.seasons.SeasonService.RulesView;
import dev.irmahs.leaderboard.stats.RulesAdminService.NewBonusInput;
import dev.irmahs.leaderboard.stats.RulesAdminService.RulesInput;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The admin Scoring screen: a season's rules. Admins of the organisation only. */
@RestController
@RequestMapping("/api/orgs/{slug}/admin/seasons/{number}/rules")
@PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
public class RulesAdminController {

    private final RulesAdminService rules;

    RulesAdminController(RulesAdminService rules) {
        this.rules = rules;
    }

    /**
     * @param apply {@code current} changes the season and works out its games again; {@code next}
     *     keeps the rules for the organisation's next season and leaves this one as it is
     */
    @PutMapping
    public RulesView save(
            @PathVariable String slug,
            @PathVariable int number,
            @RequestParam(defaultValue = "current") String apply,
            @RequestBody RulesInput input) {
        return switch (apply) {
            case "current" -> rules.save(slug, number, input);
            case "next" -> rules.saveForNextSeason(slug, number, input);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apply is current or next");
        };
    }

    @PostMapping("/bonuses")
    @ResponseStatus(HttpStatus.CREATED)
    public RulesView addBonus(@PathVariable String slug, @PathVariable int number, @RequestBody NewBonusInput input) {
        return rules.addBonus(slug, number, input);
    }

    @DeleteMapping("/bonuses/{code}")
    public RulesView removeBonus(@PathVariable String slug, @PathVariable int number, @PathVariable String code) {
        return rules.removeBonus(slug, number, code);
    }

    @PostMapping("/platform-defaults")
    public RulesView usePlatformDefaults(@PathVariable String slug, @PathVariable int number) {
        return rules.usePlatformDefaults(slug, number);
    }
}
