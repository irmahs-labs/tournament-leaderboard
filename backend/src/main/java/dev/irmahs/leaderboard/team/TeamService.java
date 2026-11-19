package dev.irmahs.leaderboard.team;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.members.MemberRole;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Members screen: who belongs to the organisation, their roles, ownership, and the invites that
 * make new admins. Any admin can invite admins, make a member an admin or a player and remove
 * members; only the owner hands ownership on. The owner can't be removed or made a player.
 */
@Service
@Transactional
public class TeamService {

    /** How long an invite can last, in minutes: 30 minutes, 1, 12, 24 or 48 hours. */
    static final Set<Integer> EXPIRY_MINUTES = Set.of(30, 60, 12 * 60, 24 * 60, 48 * 60);

    static final int MAX_EMAIL = 254;

    private final OrganizationService organizations;
    private final OrganizationMemberRepository members;
    private final InviteRepository invites;
    private final AccountService accounts;

    TeamService(
            OrganizationService organizations,
            OrganizationMemberRepository members,
            InviteRepository invites,
            AccountService accounts) {
        this.organizations = organizations;
        this.members = members;
        this.invites = invites;
        this.accounts = accounts;
    }

    /** Everyone who joined, and the invites still waiting to be used. */
    @Transactional(readOnly = true)
    public TeamView team(String slug) {
        Organization organization = organizations.requireActive(slug);
        List<OrganizationMember> all = members.findByOrganizationIdOrderByJoinedAt(organization.getId());
        List<Invite> waiting = invites.findWaiting(organization.getId(), Instant.now());
        Set<UUID> people = new HashSet<>();
        all.forEach(member -> people.add(member.getUserId()));
        waiting.forEach(invite -> {
            if (invite.getInvitedBy() != null) {
                people.add(invite.getInvitedBy());
            }
        });
        Map<UUID, AppUser> users = accounts.usersById(people);
        List<MemberView> memberViews = new ArrayList<>();
        for (OrganizationMember member : all) {
            AppUser user = users.get(member.getUserId());
            if (user != null) {
                memberViews.add(new MemberView(
                        user.getId(),
                        user.getDisplayName(),
                        user.getEmail(),
                        member.getRole(),
                        member.isOwner(),
                        member.getJoinedAt()));
            }
        }
        List<InviteView> inviteViews = waiting.stream()
                .map(invite -> InviteView.of(invite, users.get(invite.getInvitedBy())))
                .toList();
        return new TeamView(memberViews, inviteViews);
    }

    /**
     * Invites one Google account to become an admin. The code is only ever shown in this answer.
     *
     * @throws ResponseStatusException 400 when the email or the expiry is wrong, 409 when that
     *     account is already an admin
     */
    public NewInvite invite(String slug, String inviterSub, InviteInput input) {
        Organization organization = organizations.requireActive(slug);
        String email = input.email() == null ? "" : input.email().strip();
        if (email.length() > MAX_EMAIL || !looksLikeEmail(email)) {
            throw badRequest("That isn't an email address");
        }
        if (input.expiresInMinutes() == null || !EXPIRY_MINUTES.contains(input.expiresInMinutes())) {
            throw badRequest("An invite lasts 30 minutes, 1, 12, 24 or 48 hours");
        }
        List<UUID> admins = members.findByOrganizationIdOrderByJoinedAt(organization.getId()).stream()
                .filter(member -> member.getRole() == MemberRole.ADMIN)
                .map(OrganizationMember::getUserId)
                .toList();
        boolean alreadyAdmin = accounts.usersById(admins).values().stream()
                .anyMatch(user -> user.getEmail().equalsIgnoreCase(email));
        if (alreadyAdmin) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, email + " is already an admin");
        }
        UUID inviter = accounts.user(inviterSub).map(AppUser::getId).orElse(null);
        String code = InviteCodes.generate();
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(input.expiresInMinutes()));
        Invite invite = invites.save(Invite.personal(
                organization.getId(), email, InviteCodes.hash(InviteCodes.normalize(code)), inviter, expiresAt));
        return new NewInvite(invite.getId(), code, email, expiresAt);
    }

    /** Stops an invite working. Revoking it again changes nothing. */
    public void revoke(String slug, UUID inviteId) {
        Organization organization = organizations.requireActive(slug);
        invites.findByIdAndOrganizationId(inviteId, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no such invite"))
                .revoke(Instant.now());
    }

    /**
     * Makes a member an admin or a player.
     *
     * @throws ResponseStatusException 400 without a role, 404 for someone who isn't a member, 409
     *     for the owner
     */
    public void changeRole(String slug, UUID userId, MemberRole role) {
        if (role == null) {
            throw badRequest("A member is an ADMIN or a PLAYER");
        }
        OrganizationMember member = requireMember(slug, userId);
        if (member.isOwner() && role != MemberRole.ADMIN) {
            throw ownerConflict();
        }
        member.changeRole(role);
    }

    /**
     * Removes a member. Their account stays, and so does their player, scores included.
     *
     * @throws ResponseStatusException 404 for someone who isn't a member, 409 for the owner
     */
    public void remove(String slug, UUID userId) {
        OrganizationMember member = requireMember(slug, userId);
        if (member.isOwner()) {
            throw ownerConflict();
        }
        members.delete(member);
    }

    /**
     * The signed-in person leaves the organisation; their player record stays, unlinked.
     *
     * @throws ResponseStatusException 404 when they aren't a member, 409 for the owner, who hands
     *     ownership on first
     */
    public void leave(String slug, String googleSub) {
        UUID userId = accounts.user(googleSub)
                .map(AppUser::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You aren't a member"));
        remove(slug, userId);
    }

    /**
     * Hands ownership to another admin; the former owner stays an admin.
     *
     * @throws ResponseStatusException 403 when the person asking isn't the owner, 404 for someone
     *     who isn't a member, 409 when the new owner isn't an admin
     */
    public void transferOwnership(String slug, String ownerSub, UUID newOwnerId) {
        Organization organization = organizations.requireActive(slug);
        UUID ownerId = accounts.user(ownerSub)
                .map(AppUser::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can do that"));
        OrganizationMember owner = members.findById(new OrganizationMember.Key(ownerId, organization.getId()))
                .filter(OrganizationMember::isOwner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the owner can do that"));
        OrganizationMember next = requireMember(slug, newOwnerId);
        if (next == owner) {
            return;
        }
        if (next.getRole() != MemberRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Make them an admin first");
        }
        owner.giveUpOwnership();
        members.flush();
        next.takeOwnership();
    }

    /**
     * Whether a text looks like an email address: one @, something before it, and a domain with a
     * dot inside, without spaces. Google checks the rest when the person signs in.
     */
    static boolean looksLikeEmail(String text) {
        int at = text.indexOf('@');
        String domain = text.substring(at + 1);
        int dot = domain.lastIndexOf('.');
        return at > 0
                && text.indexOf('@', at + 1) < 0
                && dot > 0
                && dot < domain.length() - 1
                && text.chars().noneMatch(Character::isWhitespace);
    }

    private OrganizationMember requireMember(String slug, UUID userId) {
        Organization organization = organizations.requireActive(slug);
        return members.findById(new OrganizationMember.Key(userId, organization.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "They aren't a member"));
    }

    private static ResponseStatusException ownerConflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "The owner is always an admin: hand ownership on first");
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * @param name their name from Google; nothing when Google gave none
     */
    public record MemberView(
            UUID userId, String name, String email, MemberRole role, boolean owner, Instant joinedAt) {}

    /**
     * @param invitedBy the name of the admin who made it; nothing when they're gone
     */
    public record InviteView(UUID id, String email, String invitedBy, Instant createdAt, Instant expiresAt) {

        static InviteView of(Invite invite, AppUser inviter) {
            return new InviteView(
                    invite.getId(),
                    invite.getEmail(),
                    inviter == null ? null : inviter.getDisplayName(),
                    invite.getCreatedAt(),
                    invite.getExpiresAt());
        }
    }

    public record TeamView(List<MemberView> members, List<InviteView> invites) {}

    public record InviteInput(String email, Integer expiresInMinutes) {}

    /**
     * @param code the code, shown only now: K7QM-4FXP
     */
    public record NewInvite(UUID id, String code, String email, Instant expiresAt) {}
}
