package dev.irmahs.leaderboard.players;

import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.players.Player.NewAlias;
import dev.irmahs.leaderboard.ranks.Rank;
import dev.irmahs.leaderboard.ranks.RankController.RankView;
import dev.irmahs.leaderboard.ranks.RankRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The admin Players screen: search, add and edit an organisation's players. Players are never deleted. */
@Service
public class PlayerAdminService {

    static final int MAX_NAME = 100;
    static final int MAX_EMAIL = 254;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final OrganizationService organizations;
    private final PlayerRepository players;
    private final RankRepository ranks;

    PlayerAdminService(OrganizationService organizations, PlayerRepository players, RankRepository ranks) {
        this.organizations = organizations;
        this.players = players;
        this.ranks = ranks;
    }

    /**
     * @param query matched anywhere in the Riot ID, the Discord username or an alias, ignoring
     *     case; everyone when empty
     */
    @Transactional(readOnly = true)
    public List<PlayerView> search(String slug, String query) {
        UUID organizationId = organizations.requireActive(slug).getId();
        String pattern = "%" + escapeLike(query == null ? "" : query.strip().toLowerCase(Locale.ROOT)) + "%";
        return players.search(organizationId, pattern).stream()
                .map(PlayerView::of)
                .toList();
    }

    /**
     * @throws ResponseStatusException 400 when the details are wrong, 409 when the Riot ID is taken
     */
    @Transactional
    public PlayerView add(String slug, PlayerInput input) {
        UUID organizationId = organizations.requireActive(slug).getId();
        Details details = check(input);
        requireFree(organizationId, details.riotId(), null);
        String expected = checkEmail(input.expectedEmail());
        requireEmailFree(organizationId, expected, null);
        Player player = new Player(organizationId, details.riotId(), details.discordUsername(), details.rank());
        if (details.aliases() != null) {
            player.replaceAliases(details.aliases());
        }
        player.expectEmail(expected);
        return PlayerView.of(players.save(player));
    }

    /**
     * Changes a player's details. A new Riot ID keeps the old one as an alias; aliases left out of
     * the request stay as they are.
     *
     * @throws ResponseStatusException 404 when the organisation has no such player, 400 when the
     *     details are wrong, 409 when the Riot ID is taken
     */
    @Transactional
    public PlayerView edit(String slug, UUID playerId, PlayerInput input) {
        UUID organizationId = organizations.requireActive(slug).getId();
        Player player = players.findByIdAndOrganizationId(playerId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no player " + playerId));
        Details details = check(input);
        requireFree(organizationId, details.riotId(), playerId);
        String expected = checkEmail(input.expectedEmail());
        requireEmailFree(organizationId, expected, playerId);
        apply(player, details);
        player.expectEmail(expected);
        return PlayerView.of(player);
    }

    /**
     * A linked player changes their own Riot ID, Discord, rank and aliases; the email typed in
     * advance isn't theirs to see or change. A rank or aliases left out stay as they are.
     *
     * @throws ResponseStatusException 404 when the organisation has no such player, 400 when the
     *     details are wrong, 409 when the Riot ID is taken
     */
    @Transactional
    public PlayerView editOwn(UUID organizationId, UUID playerId, PlayerInput input) {
        Player player = players.findByIdAndOrganizationId(playerId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no player " + playerId));
        Details details = check(input);
        requireFree(organizationId, details.riotId(), playerId);
        Rank rank = input.rankId() == null ? player.getCurrentRank() : details.rank();
        apply(player, new Details(details.riotId(), details.discordUsername(), rank, details.aliases()));
        return PlayerView.of(player).withoutEmail();
    }

    private static void apply(Player player, Details details) {
        if (details.aliases() != null) {
            player.replaceAliases(details.aliases());
        }
        player.rename(details.riotId());
        player.setDiscordUsername(details.discordUsername());
        player.setCurrentRank(details.rank());
    }

    /** Lowercase, nothing when empty. */
    private static String checkEmail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String email = value.strip().toLowerCase(Locale.ROOT);
        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            throw badRequest("The Google email isn't an email address");
        }
        return email;
    }

    private void requireEmailFree(UUID organizationId, String email, UUID except) {
        if (email == null) {
            return;
        }
        players.findByOrganizationIdAndExpectedEmailIgnoreCase(organizationId, email)
                .filter(other -> !other.getId().equals(except))
                .ifPresent(other -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, other.getRiotId() + " already waits for this email");
                });
    }

    private void requireFree(UUID organizationId, String riotId, UUID except) {
        players.findByOrganizationIdAndRiotIdIgnoreCase(organizationId, riotId)
                .filter(other -> !other.getId().equals(except))
                .ifPresent(other -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, other.getRiotId() + " is already a player here");
                });
    }

    private Details check(PlayerInput input) {
        String riotId = name(input.riotId(), "Riot ID");
        if (riotId == null) {
            throw badRequest("A player needs a Riot ID");
        }
        String discord = name(input.discordUsername(), "Discord username");
        Rank rank = input.rankId() == null
                ? null
                : ranks.findById(input.rankId()).orElseThrow(() -> badRequest("There's no rank " + input.rankId()));
        List<NewAlias> aliases = null;
        if (input.aliases() != null) {
            aliases = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (AliasInput alias : input.aliases()) {
                String name = name(alias.alias(), "An alias");
                if (name == null) {
                    throw badRequest("An alias can't be empty");
                }
                if (alias.kind() == null) {
                    throw badRequest("The alias " + name + " needs a kind");
                }
                if (name.equalsIgnoreCase(riotId)) {
                    throw badRequest(name + " is the player's Riot ID, not an alias");
                }
                if (seen.add(name.toLowerCase(Locale.ROOT))) {
                    aliases.add(new NewAlias(name, alias.kind()));
                }
            }
        }
        return new Details(riotId, discord, rank, aliases);
    }

    /** The name without its surrounding spaces, or nothing when it's empty. */
    private static String name(String value, String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String name = value.strip();
        if (name.length() > MAX_NAME) {
            throw badRequest(what + " has at most " + MAX_NAME + " characters");
        }
        return name;
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    private record Details(String riotId, String discordUsername, Rank rank, List<NewAlias> aliases) {}

    /**
     * @param aliases the player's aliases as they should be; left out, they stay as they are
     */
    public record PlayerInput(
            String riotId, String discordUsername, Integer rankId, List<AliasInput> aliases, String expectedEmail) {}

    public record AliasInput(String alias, AliasKind kind) {}

    public record AliasView(String alias, AliasKind kind) {}

    /**
     * @param rank nothing when the player has none
     */
    public record PlayerView(
            UUID id,
            String riotId,
            String discordUsername,
            RankView rank,
            List<AliasView> aliases,
            String expectedEmail) {

        static PlayerView of(Player player) {
            return new PlayerView(
                    player.getId(),
                    player.getRiotId(),
                    player.getDiscordUsername(),
                    player.getCurrentRank() == null ? null : RankView.of(player.getCurrentRank()),
                    player.getAliases().stream()
                            .map(alias -> new AliasView(alias.getAlias(), alias.getKind()))
                            .toList(),
                    player.getExpectedEmail());
        }

        PlayerView withoutEmail() {
            return new PlayerView(id, riotId, discordUsername, rank, aliases, null);
        }
    }
}
