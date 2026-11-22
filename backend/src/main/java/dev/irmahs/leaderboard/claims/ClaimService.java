package dev.irmahs.leaderboard.claims;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.accounts.IdentityService;
import dev.irmahs.leaderboard.events.ClaimAnswered;
import dev.irmahs.leaderboard.events.ClaimSent;
import dev.irmahs.leaderboard.members.MemberRole;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.players.PlayerAdminService;
import dev.irmahs.leaderboard.players.PlayerAdminService.PlayerView;
import dev.irmahs.leaderboard.players.PlayerAlias;
import dev.irmahs.leaderboard.players.PlayerRepository;
import dev.irmahs.leaderboard.players.RiotIds;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Joining an organisation as a player, claiming the player record admins already enter scores for,
 * and the admins' queue of claims. Accepting links the member to the player; their stats don't move.
 * Nobody accepts their own claim while another admin exists; an organisation's only admin can, and
 * the link is marked self-approved.
 */
@Service
@Transactional
public class ClaimService {

    static final int MAX_TEXT = 200;
    static final int MAX_NAME = 100;
    static final String TAKEN = "Claimed by another account";

    private final AccountService accounts;
    private final OrganizationService organizations;
    private final OrganizationMemberRepository members;
    private final PlayerRepository players;
    private final PlayerClaimRepository claims;
    private final PlayerAdminService playerAdmin;
    private final IdentityService identity;
    private final ApplicationEventPublisher events;

    ClaimService(
            AccountService accounts,
            OrganizationService organizations,
            OrganizationMemberRepository members,
            PlayerRepository players,
            PlayerClaimRepository claims,
            PlayerAdminService playerAdmin,
            IdentityService identity,
            ApplicationEventPublisher events) {
        this.playerAdmin = playerAdmin;
        this.identity = identity;
        this.accounts = accounts;
        this.organizations = organizations;
        this.members = members;
        this.players = players;
        this.claims = claims;
        this.events = events;
    }

    /** Joins the organisation as a player; someone already a member stays as they are. */
    public ClaimState join(String slug, String googleSub) {
        Organization organization = organizations.requireActive(slug);
        AppUser user = requireUser(googleSub);
        if (members.findById(new OrganizationMember.Key(user.getId(), organization.getId()))
                .isEmpty()) {
            members.save(OrganizationMember.player(user.getId(), organization.getId()));
        }
        return mine(slug, googleSub);
    }

    /** Whether they're a member, the player they're linked to, and their latest claim. */
    @Transactional(readOnly = true)
    public ClaimState mine(String slug, String googleSub) {
        Organization organization = organizations.requireActive(slug);
        AppUser user = requireUser(googleSub);
        OrganizationMember member = members.findById(new OrganizationMember.Key(user.getId(), organization.getId()))
                .orElse(null);
        if (member == null) {
            return new ClaimState(false, null, null, null);
        }
        PlayerSummary linked = member.getPlayerId() == null
                ? null
                : players.findById(member.getPlayerId())
                        .map(player -> summary(player, 0))
                        .orElse(null);
        ClaimView claim = claims.findFirstByOrganizationIdAndUserIdAndStatusNotOrderByCreatedAtDesc(
                        organization.getId(), user.getId(), ClaimStatus.CANCELLED)
                .map(this::view)
                .orElse(null);
        return new ClaimState(true, member.getJoinedAt(), linked, claim);
    }

    /**
     * The organisation's players nobody is linked to yet, for a member to pick theirs.
     *
     * @throws ResponseStatusException 403 for someone who isn't a member
     */
    @Transactional(readOnly = true)
    public List<PlayerSummary> unclaimed(String slug, String googleSub) {
        Organization organization = organizations.requireActive(slug);
        requireMember(organization, googleSub);
        Set<UUID> linked = linkedPlayers(organization);
        Map<UUID, Long> games = claims.countGames(organization.getId()).stream()
                .collect(Collectors.toMap(GameCount::playerId, GameCount::games));
        return players.search(organization.getId(), "%").stream()
                .filter(player -> !linked.contains(player.getId()))
                .map(player -> summary(player, games.getOrDefault(player.getId(), 0L)))
                .toList();
    }

    /**
     * Asks to be linked to a player.
     *
     * @throws ResponseStatusException 400 for a note that's too long, 403 for someone who isn't a
     *     member, 404 for another organisation's player, 409 when they're already linked or have a
     *     claim waiting, or the player is already someone's
     */
    public ClaimState claim(String slug, String googleSub, NewClaim input) {
        Organization organization = organizations.requireActive(slug);
        OrganizationMember member = requireMember(organization, googleSub);
        if (member.getPlayerId() != null) {
            throw conflict("Your profile is already linked");
        }
        Player player = players.findByIdAndOrganizationId(input.playerId(), organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no such player"));
        if (members.existsByOrganizationIdAndPlayerId(organization.getId(), player.getId())) {
            throw conflict(player.getRiotId() + " is already someone's profile");
        }
        if (hasWaitingClaim(organization, member)) {
            throw conflict("Cancel the claim you sent before sending another");
        }
        claims.saveAndFlush(
                new PlayerClaim(organization.getId(), player.getId(), member.getUserId(), text(input.note())));
        events.publishEvent(new ClaimSent(organization.getId(), member.getUserId(), player.getRiotId()));
        return mine(slug, googleSub);
    }

    /**
     * A member who has never played here creates their player profile, linked to them at once with
     * no admin needed. It takes their account's Riot ID and Discord when they have one from
     * another organisation; otherwise the ones they give, which become their account's.
     *
     * @throws ResponseStatusException 400 without a Riot ID or for a name that's too long, 403 for
     *     someone who isn't a member, 409 when they're already linked or have a claim waiting, or
     *     another player here has the Riot ID
     */
    public ClaimState create(String slug, String googleSub, NewProfile input) {
        Organization organization = organizations.requireActive(slug);
        OrganizationMember member = requireMember(organization, googleSub);
        if (member.getPlayerId() != null) {
            throw conflict("Your profile is already linked");
        }
        if (hasWaitingClaim(organization, member)) {
            throw conflict("Cancel the claim you sent before creating a profile");
        }
        AppUser user = requireUser(googleSub);
        String riotId = user.getRiotId() != null ? user.getRiotId() : name(input.riotId(), "Your Riot ID");
        if (riotId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your Riot ID can't be empty");
        }
        if (user.getRiotId() == null) {
            RiotIds.require(riotId);
        }
        String discord = user.getRiotId() != null
                ? user.getDiscordUsername()
                : name(input.discordUsername(), "Your Discord username");
        if (players.findByOrganizationIdAndRiotIdIgnoreCase(organization.getId(), riotId)
                .isPresent()) {
            throw conflict(riotId + " is already a player here: claim that profile instead");
        }
        Player player;
        try {
            player = players.saveAndFlush(new Player(organization.getId(), riotId, discord, null));
            member.linkPlayer(player.getId());
            members.flush();
        } catch (DataIntegrityViolationException taken) {
            throw conflict(riotId + " is already a player here: claim that profile instead");
        }
        identity.linked(user.getId(), player);
        return mine(slug, googleSub);
    }

    /**
     * Cancels the claim waiting for an admin.
     *
     * @throws ResponseStatusException 404 when none is waiting
     */
    public ClaimState cancel(String slug, String googleSub) {
        Organization organization = organizations.requireActive(slug);
        OrganizationMember member = requireMember(organization, googleSub);
        PlayerClaim claim = claims.findFirstByOrganizationIdAndUserIdAndStatusNotOrderByCreatedAtDesc(
                        organization.getId(), member.getUserId(), ClaimStatus.CANCELLED)
                .filter(waiting -> waiting.getStatus() == ClaimStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No claim is waiting"));
        claim.cancel();
        claims.flush();
        return mine(slug, googleSub);
    }

    /**
     * The linked player changes their rank here. Their Riot ID and Discord are their account's,
     * changed on My account for every organisation at once.
     *
     * @throws ResponseStatusException 403 for someone who isn't a member, 409 when they aren't
     *     linked to a player yet, 400 for a rank that doesn't exist
     */
    public PlayerView editMine(String slug, String googleSub, OwnDetails input) {
        Organization organization = organizations.requireActive(slug);
        OrganizationMember member = requireMember(organization, googleSub);
        if (member.getPlayerId() == null) {
            throw conflict("Claim your profile first");
        }
        return playerAdmin.rerank(organization.getId(), member.getPlayerId(), input.rankId());
    }

    /** The admins' queue: the claims in this state, oldest first. */
    @Transactional(readOnly = true)
    public List<ClaimRow> queue(String slug, String adminSub, ClaimStatus status) {
        Organization organization = organizations.requireActive(slug);
        UUID admin = requireUser(adminSub).getId();
        List<PlayerClaim> found = claims.findByOrganizationIdAndStatusOrderByCreatedAt(
                organization.getId(), status == null ? ClaimStatus.PENDING : status);
        Map<UUID, AppUser> users =
                accounts.usersById(found.stream().map(PlayerClaim::getUserId).toList());
        Map<UUID, Long> games = claims.countGames(organization.getId()).stream()
                .collect(Collectors.toMap(GameCount::playerId, GameCount::games));
        Map<UUID, Long> competing =
                claims.findByOrganizationIdAndStatusOrderByCreatedAt(organization.getId(), ClaimStatus.PENDING).stream()
                        .collect(Collectors.groupingBy(PlayerClaim::getPlayerId, Collectors.counting()));
        return found.stream()
                .map(claim -> {
                    AppUser claimant = users.get(claim.getUserId());
                    Instant since = members.findById(
                                    new OrganizationMember.Key(claim.getUserId(), organization.getId()))
                            .map(OrganizationMember::getJoinedAt)
                            .orElse(null);
                    Player player = players.findById(claim.getPlayerId()).orElseThrow();
                    return new ClaimRow(
                            claim.getId(),
                            claimant == null ? null : claimant.getDisplayName(),
                            claimant == null ? null : claimant.getEmail(),
                            since,
                            summary(player, games.getOrDefault(player.getId(), 0L)),
                            competing.getOrDefault(player.getId(), 0L),
                            claim.getUserId().equals(admin),
                            view(claim));
                })
                .toList();
    }

    /**
     * Accepts a claim: links the member to the player, and declines the other claims waiting for
     * the same player.
     *
     * @throws ResponseStatusException 403 for an admin's own claim while another admin exists, 404
     *     for an unknown claim, 409 when it's no longer waiting, the player is already someone's or
     *     the member left
     */
    public ClaimRow accept(String slug, String adminSub, UUID id) {
        Organization organization = organizations.requireActive(slug);
        UUID admin = requireUser(adminSub).getId();
        PlayerClaim claim = requireWaiting(organization, id);
        boolean own = claim.getUserId().equals(admin);
        if (own && members.countByOrganizationIdAndRole(organization.getId(), MemberRole.ADMIN) > 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Another admin accepts your own claim");
        }
        OrganizationMember member = members.findById(
                        new OrganizationMember.Key(claim.getUserId(), organization.getId()))
                .orElseThrow(() -> conflict("This member left the organisation"));
        if (members.existsByOrganizationIdAndPlayerId(organization.getId(), claim.getPlayerId())) {
            throw conflict("This player is already someone's profile");
        }
        member.linkPlayer(claim.getPlayerId());
        identity.linked(claim.getUserId(), players.findById(claim.getPlayerId()).orElseThrow());
        claim.accept(admin, own);
        for (PlayerClaim other : claims.findByOrganizationIdAndPlayerIdAndStatus(
                organization.getId(), claim.getPlayerId(), ClaimStatus.PENDING)) {
            if (!other.getId().equals(claim.getId())) {
                other.decline(admin, TAKEN);
            }
        }
        claims.flush();
        events.publishEvent(new ClaimAnswered(organization.getId(), claim.getUserId(), riotIdOf(claim), true, null));
        return row(slug, adminSub, claim);
    }

    /**
     * Declines a claim, with a reason the player sees.
     *
     * @throws ResponseStatusException 400 for a reason that's too long, 404 for an unknown claim, 409
     *     when it's no longer waiting
     */
    public ClaimRow decline(String slug, String adminSub, UUID id, String reason) {
        Organization organization = organizations.requireActive(slug);
        UUID admin = requireUser(adminSub).getId();
        PlayerClaim claim = requireWaiting(organization, id);
        claim.decline(admin, text(reason));
        claims.flush();
        events.publishEvent(
                new ClaimAnswered(organization.getId(), claim.getUserId(), riotIdOf(claim), false, claim.getReason()));
        return row(slug, adminSub, claim);
    }

    private String riotIdOf(PlayerClaim claim) {
        return players.findById(claim.getPlayerId()).map(Player::getRiotId).orElse(null);
    }

    private ClaimRow row(String slug, String adminSub, PlayerClaim claim) {
        return queue(slug, adminSub, claim.getStatus()).stream()
                .filter(each -> each.id().equals(claim.getId()))
                .findFirst()
                .orElseThrow();
    }

    private PlayerClaim requireWaiting(Organization organization, UUID id) {
        PlayerClaim claim = claims.findByIdAndOrganizationId(id, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no such claim"));
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw conflict("This claim was already answered");
        }
        return claim;
    }

    private boolean hasWaitingClaim(Organization organization, OrganizationMember member) {
        return claims.findFirstByOrganizationIdAndUserIdAndStatusNotOrderByCreatedAtDesc(
                        organization.getId(), member.getUserId(), ClaimStatus.CANCELLED)
                .filter(claim -> claim.getStatus() == ClaimStatus.PENDING)
                .isPresent();
    }

    private Set<UUID> linkedPlayers(Organization organization) {
        return members.findByOrganizationIdAndPlayerIdNotNull(organization.getId()).stream()
                .map(OrganizationMember::getPlayerId)
                .collect(Collectors.toSet());
    }

    private OrganizationMember requireMember(Organization organization, String googleSub) {
        AppUser user = requireUser(googleSub);
        return members.findById(new OrganizationMember.Key(user.getId(), organization.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Join " + organization.getName() + " as a player first"));
    }

    private AppUser requireUser(String googleSub) {
        return accounts.user(googleSub)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again"));
    }

    private ClaimView view(PlayerClaim claim) {
        String riotId =
                players.findById(claim.getPlayerId()).map(Player::getRiotId).orElse(null);
        return new ClaimView(
                claim.getId(),
                claim.getPlayerId(),
                riotId,
                claim.getNote(),
                claim.getCreatedAt(),
                claim.getStatus(),
                claim.getReason(),
                claim.isSelfApproved(),
                claim.getDecidedAt());
    }

    private static PlayerSummary summary(Player player, long games) {
        return new PlayerSummary(
                player.getId(),
                player.getRiotId(),
                player.getDiscordUsername(),
                player.getAliases().stream().map(PlayerAlias::getAlias).toList(),
                player.getCurrentRank() == null ? null : player.getCurrentRank().getRankName(),
                games);
    }

    /** A Riot ID or Discord username without its surrounding spaces, nothing when empty. */
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

    /** Trimmed, nothing when empty. */
    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > MAX_TEXT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep it to " + MAX_TEXT + " characters");
        }
        return trimmed;
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    /** A player record, as a member picks theirs. */
    public record PlayerSummary(
            UUID id, String riotId, String discordUsername, List<String> aliases, String rank, long games) {}

    /** A claim, as its member or an admin sees it. */
    public record ClaimView(
            UUID id,
            UUID playerId,
            String riotId,
            String note,
            Instant sentAt,
            ClaimStatus status,
            String reason,
            boolean selfApproved,
            Instant decidedAt) {}

    /**
     * Where someone stands with the organisation's player records.
     *
     * @param linked the player they're linked to; nothing until a claim is accepted
     * @param claim their latest claim not cancelled: the one waiting, or the last answered
     */
    public record ClaimState(boolean member, Instant joinedAt, PlayerSummary linked, ClaimView claim) {}

    /**
     * A claim in the admins' queue.
     *
     * @param competing how many claims are waiting for the same player, this one included
     * @param own whether it's the admin's own claim
     */
    public record ClaimRow(
            UUID id,
            String name,
            String email,
            Instant memberSince,
            PlayerSummary player,
            long competing,
            boolean own,
            ClaimView claim) {}

    public record NewClaim(UUID playerId, String note) {}

    /** The Riot ID and Discord of a new profile; left out when the account already has them. */
    public record NewProfile(String riotId, String discordUsername) {}

    /** What a linked player changes about themselves in one organisation: an empty rank is Unranked. */
    public record OwnDetails(Integer rankId) {}
}
