package dev.irmahs.leaderboard.players;

import dev.irmahs.leaderboard.organizations.OrganizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The organisation's players for the public Players page: who they are and their other names. */
@RestController
public class PlayerController {

    private final OrganizationService organizations;
    private final PlayerRepository players;

    PlayerController(OrganizationService organizations, PlayerRepository players) {
        this.organizations = organizations;
        this.players = players;
    }

    @GetMapping("/api/orgs/{slug}/players")
    @Transactional(readOnly = true)
    public List<PublicPlayer> players(@PathVariable String slug) {
        UUID organizationId = organizations.requireActive(slug).getId();
        return players.search(organizationId, "%").stream()
                .map(player -> new PublicPlayer(
                        player.getId(),
                        player.getRiotId(),
                        player.getDiscordUsername(),
                        player.getAliases().stream().map(PlayerAlias::getAlias).toList()))
                .toList();
    }

    /**
     * @param aliases their other names: previous Riot IDs and nicknames
     */
    public record PublicPlayer(UUID id, String riotId, String discordUsername, List<String> aliases) {}
}
