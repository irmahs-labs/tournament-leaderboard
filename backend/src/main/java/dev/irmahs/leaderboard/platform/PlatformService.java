package dev.irmahs.leaderboard.platform;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.events.OrganizationStatusChanged;
import dev.irmahs.leaderboard.members.MemberCount;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.organizations.OrganizationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The platform admin's Organisations screen: every organisation, with its owner, members and
 * activity, suspending or reactivating one, and deleting one nobody uses yet. Platform admins only.
 */
@Service
@Transactional
public class PlatformService {

    private final AccountService accounts;
    private final OrganizationService organizations;
    private final OrganizationMemberRepository members;
    private final ApplicationEventPublisher events;
    private final EmptyOrganizations empty;

    PlatformService(
            AccountService accounts,
            OrganizationService organizations,
            OrganizationMemberRepository members,
            ApplicationEventPublisher events,
            EmptyOrganizations empty) {
        this.accounts = accounts;
        this.organizations = organizations;
        this.members = members;
        this.events = events;
        this.empty = empty;
    }

    /**
     * @throws ResponseStatusException 403 for anyone but a platform admin
     */
    @Transactional(readOnly = true)
    public List<PlatformOrganization> organizations(String googleSub) {
        requirePlatformAdmin(googleSub);
        Map<UUID, Long> counts = members.countByOrganization().stream()
                .collect(Collectors.toMap(MemberCount::organizationId, MemberCount::members));
        List<OrganizationMember> owners = members.findByOwnerTrue();
        Map<UUID, AppUser> users = accounts.usersById(
                owners.stream().map(OrganizationMember::getUserId).toList());
        Map<UUID, String> ownerEmails = owners.stream()
                .filter(owner -> users.containsKey(owner.getUserId()))
                .collect(Collectors.toMap(
                        OrganizationMember::getOrganizationId,
                        owner -> users.get(owner.getUserId()).getEmail()));
        Set<UUID> kept = empty.kept();
        String featured =
                organizations.mostRecentlyActive().map(Organization::getSlug).orElse(null);
        return organizations.all().stream()
                .map(organization -> new PlatformOrganization(
                        organization.getSlug(),
                        organization.getName(),
                        ownerEmails.get(organization.getId()),
                        organization.getCreatedAt(),
                        organization.getLastActivityAt(),
                        counts.getOrDefault(organization.getId(), 0L),
                        organization.getStatus(),
                        organization.getSlug().equals(featured),
                        !kept.contains(organization.getId())))
                .toList();
    }

    /**
     * @throws ResponseStatusException 400 without a status, 403 for anyone but a platform admin,
     *     404 for an unknown slug
     */
    public void changeStatus(String googleSub, String slug, OrganizationStatus status) {
        requirePlatformAdmin(googleSub);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The status is ACTIVE or SUSPENDED");
        }
        Organization organization = organizations.changeStatus(slug, status);
        events.publishEvent(
                new OrganizationStatusChanged(organization.getId(), status == OrganizationStatus.SUSPENDED));
    }

    /**
     * Deletes an organisation nobody uses yet, once the platform admin typed its name.
     *
     * @throws ResponseStatusException 400 when the name typed isn't the organisation's, 403 for
     *     anyone but a platform admin, 404 for an unknown slug, 409 when it has a game or more than
     *     one admin
     */
    public void delete(String googleSub, String slug, String typedName) {
        requirePlatformAdmin(googleSub);
        Organization organization = organizations.all().stream()
                .filter(each -> each.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such organisation"));
        if (typedName == null || !typedName.trim().equals(organization.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type the organisation's name to delete it");
        }
        if (!empty.deletable(organization.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only an organisation with no games and one admin at most can be deleted");
        }
        empty.delete(organization.getId());
    }

    private void requirePlatformAdmin(String googleSub) {
        if (!accounts.user(googleSub).map(AppUser::isPlatformAdmin).orElse(false)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform admins only");
        }
    }

    /**
     * One row of the screen.
     *
     * @param ownerEmail empty for an organisation without an owner, such as one just imported
     * @param featured whether the home page shows it
     * @param deletable whether it can be deleted: no game, one admin at most
     */
    public record PlatformOrganization(
            String slug,
            String name,
            String ownerEmail,
            Instant createdAt,
            Instant lastActivityAt,
            long members,
            OrganizationStatus status,
            boolean featured,
            boolean deletable) {}
}
