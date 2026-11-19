package dev.irmahs.leaderboard.members;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, OrganizationMember.Key> {

    boolean existsByOrganizationIdAndOwnerTrue(UUID organizationId);

    /** Every organisation's owner. */
    List<OrganizationMember> findByOwnerTrue();

    /** How many members each organisation has; one with none isn't listed. */
    @Query("""
            select new dev.irmahs.leaderboard.members.MemberCount(m.organizationId, count(m))
            from OrganizationMember m group by m.organizationId""")
    List<MemberCount> countByOrganization();

    long countByOrganizationIdAndRole(UUID organizationId, MemberRole role);

    boolean existsByOrganizationIdAndPlayerId(UUID organizationId, UUID playerId);

    /** The members already linked to a player. */
    List<OrganizationMember> findByOrganizationIdAndPlayerIdNotNull(UUID organizationId);

    /** How many organisations someone owns. */
    long countByUserIdAndOwnerTrue(UUID userId);

    /** The organisation's members, the earliest first. */
    List<OrganizationMember> findByOrganizationIdOrderByJoinedAt(UUID organizationId);

    /** An account's memberships of active organisations, by organisation name. */
    @Query("""
            select new dev.irmahs.leaderboard.members.Membership(o.slug, o.name, m.role, m.owner)
            from OrganizationMember m, Organization o
            where o.id = m.organizationId and m.userId = :userId
            and o.status = dev.irmahs.leaderboard.organizations.OrganizationStatus.ACTIVE
            order by o.name
            """)
    List<Membership> findMemberships(UUID userId);
}
