package dev.irmahs.leaderboard.accounts;

import dev.irmahs.leaderboard.accounts.AccessProperties.InitialMember;
import dev.irmahs.leaderboard.members.Membership;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Accounts: created and kept up to date at each sign-in, and read for /api/me. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AppUserRepository users;
    private final OrganizationMemberRepository members;
    private final OrganizationService organizations;
    private final AccessProperties access;

    AccountService(
            AppUserRepository users,
            OrganizationMemberRepository members,
            OrganizationService organizations,
            AccessProperties access) {
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
        return user;
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

    /** The signed-in person, or nothing when their account no longer exists. */
    @Transactional(readOnly = true)
    public Optional<Me> me(String googleSub) {
        return users.findByGoogleSub(googleSub)
                .map(user -> new Me(
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getAvatarUrl(),
                        user.isPlatformAdmin(),
                        members.findMemberships(user.getId())));
    }

    /**
     * @param memberships the active organisations they belong to
     */
    public record Me(
            String email, String displayName, String avatarUrl, boolean platformAdmin, List<Membership> memberships) {}
}
