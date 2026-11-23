package dev.irmahs.leaderboard.team;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByCodeHash(byte[] codeHash);

    /** The same invite, its row locked until the transaction ends, so two people can't both use the last place. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from Invite invite where invite.codeHash = :codeHash")
    Optional<Invite> findForUpdate(byte[] codeHash);

    /** The organisation's invites that can still be used, the soonest to expire first. */
    @Query("""
            select invite from Invite invite
            where invite.organizationId = :organizationId and invite.revokedAt is null
            and invite.expiresAt > :now and (invite.maxUses is null or invite.useCount < invite.maxUses)
            order by invite.expiresAt
            """)
    List<Invite> findWaiting(UUID organizationId, Instant now);

    Optional<Invite> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
