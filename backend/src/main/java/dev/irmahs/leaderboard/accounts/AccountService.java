package dev.irmahs.leaderboard.accounts;

import dev.irmahs.leaderboard.accounts.AccessProperties.InitialMember;
import dev.irmahs.leaderboard.members.MemberRole;
import dev.irmahs.leaderboard.members.Membership;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.players.PlayerRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Accounts: created and kept up to date at each sign-in, and read for /api/me. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AppUserRepository users;
    private final OrganizationMemberRepository members;
    private final OrganizationService organizations;
    private final AccessProperties access;
    private final PlayerRepository players;
    private final IdentityService identity;

    AccountService(
            AppUserRepository users,
            OrganizationMemberRepository members,
            OrganizationService organizations,
            AccessProperties access,
            PlayerRepository players,
            IdentityService identity) {
        this.players = players;
        this.identity = identity;
        this.users = users;
        this.members = members;
        this.organizations = organizations;
        this.access = access;
    }

    /**
     * Creates the account at someone's first sign-in, then keeps it up to date with Google's
     * details. Adds them to any organisation the configuration lists them for, if they're not
     * a member yet.
     */
    @Transactional
    public AppUser signIn(GoogleProfile profile) {
        boolean platformAdmin = access.isPlatformAdmin(profile.email());
        AppUser user = users.findByGoogleSub(profile.sub())
                .map(existing -> {
                    existing.update(profile, platformAdmin);
                    return existing;
                })
                .orElseGet(() -> users.save(new AppUser(profile, platformAdmin)));
        users.flush();
        for (InitialMember initial : access.initialMembershipsOf(profile.email())) {
            organizations
                    .findActive(initial.slug())
                    .ifPresentOrElse(
                            organization -> join(user.getId(), organization.getId(), initial),
                            () -> log.warn("Initial member for {}: there's no such organisation", initial.slug()));
        }
        for (Player player : players.findByExpectedEmailIgnoreCase(profile.email())) {
            linkExpected(user.getId(), player);
        }
        return user;
    }

    /**
     * Someone signed in with the email an admin typed on a player: they join as a player if they
     * aren't a member, and are linked to it unless they or the player already are. The email is
     * then used up.
     */
    private void linkExpected(UUID userId, Player player) {
        UUID organizationId = player.getOrganizationId();
        if (organizations.findActive(organizationId).isEmpty()) {
            return;
        }
        OrganizationMember member = members.findById(new OrganizationMember.Key(userId, organizationId))
                .orElseGet(() -> members.save(OrganizationMember.player(userId, organizationId)));
        if (member.getPlayerId() == null
                && !members.existsByOrganizationIdAndPlayerId(organizationId, player.getId())) {
            member.linkPlayer(player.getId());
            player.expectEmail(null);
            identity.linked(userId, player);
            log.info("Linked a member to their player by the email expected for it");
        }
    }

    private void join(UUID userId, UUID organizationId, InitialMember initial) {
        if (members.existsById(new OrganizationMember.Key(userId, organizationId))) {
            return;
        }
        if (initial.owner() && !members.existsByOrganizationIdAndOwnerTrue(organizationId)) {
            members.save(OrganizationMember.owner(userId, organizationId));
        } else {
            if (initial.owner()) {
                log.warn("{} already has an owner: adding the initial owner as an admin", initial.slug());
            }
            members.save(OrganizationMember.admin(userId, organizationId));
        }
    }

    /**
     * Whether the account with this Google id runs the active organisation: one of its admins, or
     * a platform admin, who runs every organisation.
     */
    @Transactional(readOnly = true)
    public boolean isAdmin(String googleSub, String slug) {
        return users.findByGoogleSub(googleSub)
                .flatMap(user -> organizations
                        .findActive(slug)
                        .map(organization -> user.isPlatformAdmin()
                                || members.findById(new OrganizationMember.Key(user.getId(), organization.getId()))
                                        .filter(member -> member.getRole() == MemberRole.ADMIN)
                                        .isPresent()))
                .orElse(false);
    }

    /** The signed-in person, or nothing when their account no longer exists. */
    @Transactional(readOnly = true)
    public Optional<Me> me(String googleSub) {
        return users.findByGoogleSub(googleSub)
                .map(user -> new Me(
                        user.getEmail(),
                        user.getDisplayName(),
                        user.isPlatformAdmin(),
                        user.getRiotId(),
                        user.getDiscordUsername(),
                        members.findMemberships(user.getId()),
                        members.findSuspendedMemberships(user.getId())));
    }

    /**
     * Deletes the signed-in person's account and, with it, their memberships. Player records and
     * results stay with their organisations; organisations and invites they created keep going.
     *
     * @throws ResponseStatusException 409 while they own an organisation: they hand it on first
     */
    @Transactional
    public void delete(String googleSub) {
        users.findByGoogleSub(googleSub).ifPresent(user -> {
            if (members.countByUserIdAndOwnerTrue(user.getId()) > 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Hand ownership of your organisations on before deleting your account");
            }
            users.delete(user);
            users.flush();
        });
    }

    /** The account with this Google id, or nothing when it no longer exists. */
    @Transactional(readOnly = true)
    public Optional<AppUser> user(String googleSub) {
        return users.findByGoogleSub(googleSub);
    }

    /** The accounts with these ids, by id; an id with no account is left out. */
    @Transactional(readOnly = true)
    public Map<UUID, AppUser> usersById(Collection<UUID> ids) {
        Map<UUID, AppUser> byId = new HashMap<>();
        users.findAllById(ids).forEach(user -> byId.put(user.getId(), user));
        return byId;
    }

    /**
     * @param riotId shown on every player profile linked to the account; nothing before the first
     * @param memberships the active organisations they belong to
     * @param suspended the organisations they belong to that the platform admin suspended: their
     *     pages are hidden, but members are told why
     */
    public record Me(
            String email,
            String displayName,
            boolean platformAdmin,
            String riotId,
            String discordUsername,
            List<Membership> memberships,
            List<Membership> suspended) {}
}
