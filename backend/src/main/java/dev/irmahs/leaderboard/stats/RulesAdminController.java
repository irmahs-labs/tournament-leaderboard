package dev.irmahs.leaderboard.stats;

import dev.irmahs.leaderboard.seasons.SeasonService.RulesView;
import dev.irmahs.leaderboard.stats.RulesAdminService.RulesInput;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The admin Scoring screen: a season's rules. Admins of the organisation only. */
@RestController
@RequestMapping("/api/orgs/{slug}/admin/seasons/{number}/rules")
@PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
public class RulesAdminController {

    private final RulesAdminService rules;

    RulesAdminController(RulesAdminService rules) {
        this.rules = rules;
    }

    @PutMapping
    public RulesView save(@PathVariable String slug, @PathVariable int number, @RequestBody RulesInput input) {
        return rules.save(slug, number, input);
    }

    @PostMapping("/platform-defaults")
    public RulesView usePlatformDefaults(@PathVariable String slug, @PathVariable int number) {
        return rules.usePlatformDefaults(slug, number);
    }
}
