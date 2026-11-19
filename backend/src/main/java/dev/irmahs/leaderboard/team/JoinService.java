package dev.irmahs.leaderboard.team;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.members.MemberRole;
import dev.irmahs.leaderboard.members.Membership;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The join page: what a code is for, and using it. A personal invite only works for the Google
 * account it was made for, once; it makes that account an admin, or a player an admin.
 */
@Service
@Transactional
public class JoinService {

    private final InviteRepository invites;
    private final OrganizationService organizations;
    private final OrganizationMemberRepository members;
    private final AccountService accounts;

    JoinService(
            InviteRepository invites,
            OrganizationService organizations,
            OrganizationMemberRepository members,
            AccountService accounts) {
        this.invites = invites;
        this.organizations = organizations;
        this.members = members;
        this.accounts = accounts;
    }

    /**
     * What a code is for, even when it can't be used any more, so the page can say why.
     *
     * @throws ResponseStatusException 404 for a code that doesn't exist, or whose organisation is
     *     gone or suspended
     */
    @Transactional(readOnly = true)
    public JoinView look(String typed) {
        Invite invite = find(typed);
        Organization organization = requireOrganization(invite);
        String invitedBy = invite.getInvitedBy() == null
                ? null
                : accounts.usersById(List.of(invite.getInvitedBy())).values().stream()
                        .map(AppUser::getDisplayName)
                        .findFirst()
                        .orElse(null);
        return new JoinView(
                new OrganizationRef(organization.getSlug(), organization.getName(), organization.getTournamentName()),
                invite.getRole(),
                invite.getEmail(),
                invitedBy,
                invite.getExpiresAt(),
                invite.state(Instant.now()));
    }

    /**
     * Uses the code for the signed-in account. Someone already an admin stays one.
     *
     * @throws ResponseStatusException 403 when the invite is for another email, 404 for a code that
     *     doesn't exist, 410 when it expired, was used or was revoked
     */
    public Membership accept(String typed, String googleSub) {
        String code = InviteCodes.normalize(typed);
        if (code == null) {
            throw notFound();
        }
        Invite invite = invites.findForUpdate(InviteCodes.hash(code)).orElseThrow(JoinService::notFound);
        Organization organization = requireOrganization(invite);
        InviteState state = invite.state(Instant.now());
        if (state != InviteState.VALID) {
            throw new ResponseStatusException(HttpStatus.GONE, state.name());
        }
        AppUser user = accounts.user(googleSub)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again"));
        if (invite.getKind() == Invite.Kind.PERSONAL && !user.getEmail().equalsIgnoreCase(invite.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This invite is for another Google account");
        }
        OrganizationMember member = members.findById(new OrganizationMember.Key(user.getId(), organization.getId()))
                .orElseGet(() -> members.save(
                        invite.getRole() == MemberRole.ADMIN
                                ? OrganizationMember.admin(user.getId(), organization.getId())
                                : OrganizationMember.player(user.getId(), organization.getId())));
        if (invite.getRole() == MemberRole.ADMIN) {
            member.changeRole(MemberRole.ADMIN);
        }
        invite.use();
        return new Membership(organization.getSlug(), organization.getName(), member.getRole(), member.isOwner());
    }

    private Invite find(String typed) {
        String code = InviteCodes.normalize(typed);
        if (code == null) {
            throw notFound();
        }
        return invites.findByCodeHash(InviteCodes.hash(code)).orElseThrow(JoinService::notFound);
    }

    private Organization requireOrganization(Invite invite) {
        return organizations.findActive(invite.getOrganizationId()).orElseThrow(JoinService::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no such code");
    }

    public record OrganizationRef(String slug, String name, String tournamentName) {}

    /**
     * @param email for a personal invite, the Google account it's for
     * @param invitedBy the name of the admin who made it; nothing when they're gone
     * @param state whether it can still be used, and if not, why
     */
    public record JoinView(
            OrganizationRef organization,
            MemberRole role,
            String email,
            String invitedBy,
            Instant expiresAt,
            InviteState state) {}
}
