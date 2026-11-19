package dev.irmahs.leaderboard.seasons;

import dev.irmahs.leaderboard.organizations.OrganizationService;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * The rules an organisation saved with "Next season only": kept apart from every season until a new
 * season is started with them.
 */
@Service
public class NextSeasonRules {

    private final OrganizationService organizations;
    private final JsonMapper json;

    NextSeasonRules(OrganizationService organizations, JsonMapper json) {
        this.organizations = organizations;
        this.json = json;
    }

    public Optional<RulesSnapshot> load(UUID organizationId) {
        return organizations.nextSeasonRules(organizationId).map(text -> json.readValue(text, RulesSnapshot.class));
    }

    public void save(UUID organizationId, RulesSnapshot rules) {
        organizations.keepNextSeasonRules(organizationId, json.writeValueAsString(rules));
    }

    public void clear(UUID organizationId) {
        organizations.keepNextSeasonRules(organizationId, null);
    }
}
