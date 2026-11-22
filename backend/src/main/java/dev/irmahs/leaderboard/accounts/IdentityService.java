package dev.irmahs.leaderboard.accounts;

import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.players.PlayerRepository;
import dev.irmahs.leaderboard.players.RiotIds;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * A person's Riot ID and Discord username: kept on their account and shown the same on every
 * player profile linked to it, at every organisation. Stats, rank and aliases stay each
 * organisation's own.
 */
@Service
public class IdentityService {

    static final int MAX_NAME = 100;

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    private final AppUserRepository users;
    private final OrganizationMemberRepository members;
    private final PlayerRepository players;
    private final OrganizationService organizations;

    IdentityService(
            AppUserRepository users,
            OrganizationMemberRepository members,
            PlayerRepository players,
            OrganizationService organizations) {
        this.users = users;
        this.members = members;
        this.players = players;
        this.organizations = organizations;
    }

    /**
     * Changes the signed-in person's Riot ID and Discord username, on their account and on each
     * linked profile. A new Riot ID keeps the old one as an alias at each organisation.
     *
     * @throws ResponseStatusException 400 for an empty Riot ID or a name that's too long, 401
     *     without an account, 409 when another player of one of their organisations has the Riot ID
     */
    @Transactional
    public void change(String googleSub, IdentityInput input) {
        AppUser user = users.findByGoogleSub(googleSub)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again"));
        String riotId = name(input.riotId(), "Your Riot ID");
        if (riotId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your Riot ID can't be empty");
        }
        RiotIds.require(riotId);
        String discord = name(input.discordUsername(), "Your Discord username");
        List<Player> linked = linkedPlayers(user.getId());
        List<String> taken = linked.stream()
                .filter(player -> !freeFor(player, riotId))
                .map(player -> organizationName(player.getOrganizationId()))
                .toList();
        if (!taken.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, riotId + " is already another player at " + String.join(", ", taken));
        }
        for (Player player : linked) {
            player.rename(riotId);
            player.setDiscordUsername(discord);
        }
        user.identify(riotId, discord);
    }

    /**
     * Someone was just linked to a player profile. Their first one gives the account its Riot ID
     * and Discord; a later one takes the account's, unless another player there already has
     * that Riot ID, which only an admin can sort out.
     */
    @Transactional
    public void linked(UUID userId, Player player) {
        users.findById(userId).ifPresent(user -> {
            if (user.getRiotId() == null) {
                user.identify(player.getRiotId(), player.getDiscordUsername());
                return;
            }
            if (freeFor(player, user.getRiotId())) {
                player.rename(user.getRiotId());
            } else {
                log.warn("Kept a newly linked profile's Riot ID: another player there has the account's");
            }
            if (user.getDiscordUsername() != null) {
                player.setDiscordUsername(user.getDiscordUsername());
            }
        });
    }

    private List<Player> linkedPlayers(UUID userId) {
        return members.findByUserIdAndPlayerIdNotNull(userId).stream()
                .map(OrganizationMember::getPlayerId)
                .map(players::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    /** Whether no other player of the profile's organisation has this Riot ID. */
    private boolean freeFor(Player player, String riotId) {
        return players.findByOrganizationIdAndRiotIdIgnoreCase(player.getOrganizationId(), riotId)
                .filter(other -> !other.getId().equals(player.getId()))
                .isEmpty();
    }

    private String organizationName(UUID organizationId) {
        return organizations
                .findActive(organizationId)
                .map(Organization::getName)
                .orElse("another organisation");
    }

    /** The name without its surrounding spaces, or nothing when it's empty. */
    private static String name(String value, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String name = value.strip();
        if (name.length() > MAX_NAME) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, what + " has at most " + MAX_NAME + " characters");
        }
        return name;
    }

    public record IdentityInput(String riotId, String discordUsername) {}
}
