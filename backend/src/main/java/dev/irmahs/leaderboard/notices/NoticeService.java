package dev.irmahs.leaderboard.notices;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.events.AdminChanged;
import dev.irmahs.leaderboard.events.ClaimAnswered;
import dev.irmahs.leaderboard.events.ClaimSent;
import dev.irmahs.leaderboard.events.MatchChanged;
import dev.irmahs.leaderboard.events.OrganizationStatusChanged;
import dev.irmahs.leaderboard.matches.Match;
import dev.irmahs.leaderboard.matches.MatchRepository;
import dev.irmahs.leaderboard.members.MemberRole;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.notices.NoticeStore.Notice;
import dev.irmahs.leaderboard.rosters.MovedUp;
import dev.irmahs.leaderboard.rosters.Reminded;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notices in the app. Each one is created from an event, in the same transaction as the change it
 * tells about, so a change that fails leaves no notice. A match planned, moved or called off waits
 * {@link #SETTLE} before it's shown, so an admin fixing a typo creates one notice, not three.
 * Notices read more than {@link #KEEP_READ} ago are forgotten.
 */
@Service
@Transactional
public class NoticeService {

    static final Duration SETTLE = Duration.ofMinutes(10);
    static final Duration KEEP_READ = Duration.ofDays(90);
    static final int SHOWN = 50;

    private final NoticeStore store;
    private final AccountService accounts;
    private final OrganizationMemberRepository members;
    private final MatchRepository matches;

    NoticeService(
            NoticeStore store, AccountService accounts, OrganizationMemberRepository members, MatchRepository matches) {
        this.store = store;
        this.accounts = accounts;
        this.members = members;
        this.matches = matches;
    }

    // Reading.

    /** The signed-in person's latest notices, and how many are unread. */
    @Transactional(readOnly = true)
    public Notices mine(String googleSub) {
        UUID userId = accounts.requireUser(googleSub).getId();
        Instant now = Instant.now();
        return new Notices(store.unread(userId, now), store.shown(userId, now, SHOWN));
    }

    /** Marks every notice shown so far as read. */
    public void read(String googleSub) {
        store.markRead(accounts.requireUser(googleSub).getId(), Instant.now());
    }

    // Players.

    @EventListener
    void matchChanged(MatchChanged event) {
        Match match = matches.findById(event.matchId()).orElseThrow();
        NoticeKind kind = NoticeKind.valueOf("MATCH_" + event.change());
        String subject = "match:" + match.getId();
        Instant shownFrom = Instant.now().plus(SETTLE);
        Map<String, Object> details = matchDetails(match);
        List<UUID> everyone = members.findByOrganizationIdAndPlayerIdNotNull(event.organizationId()).stream()
                .map(OrganizationMember::getUserId)
                .toList();
        for (UUID userId : everyone) {
            NoticeKind merged = store.pending(userId, subject, Instant.now())
                    // Planned, then moved before anyone saw it: still news of a match planned.
                    .filter(before -> before == NoticeKind.MATCH_PLANNED && kind == NoticeKind.MATCH_MOVED)
                    .orElse(kind);
            if (!store.replaceUnread(userId, subject, merged, details, shownFrom)) {
                store.add(userId, event.organizationId(), merged, subject, details, shownFrom);
            }
        }
    }

    @EventListener
    void reminded(Reminded event) {
        Match match = matches.findById(event.matchId()).orElseThrow();
        String subject = "reminder:" + match.getId();
        Map<String, Object> details = matchDetails(match);
        for (UUID userId : usersOf(event.organizationId(), event.players())) {
            if (!store.replaceUnread(userId, subject, NoticeKind.REMINDER, details, Instant.now())) {
                store.add(userId, event.organizationId(), NoticeKind.REMINDER, subject, details, Instant.now());
            }
        }
    }

    @EventListener
    void movedUp(MovedUp event) {
        Match match = matches.findById(event.matchId()).orElseThrow();
        for (UUID userId : usersOf(event.organizationId(), List.of(event.playerId()))) {
            store.add(userId, event.organizationId(), NoticeKind.MOVED_UP, null, matchDetails(match), Instant.now());
        }
    }

    @EventListener
    void claimAnswered(ClaimAnswered event) {
        Map<String, Object> details = new HashMap<>();
        details.put("riotId", event.riotId());
        details.put("accepted", event.accepted());
        details.put("reason", event.reason());
        store.add(event.claimantId(), event.organizationId(), NoticeKind.CLAIM_ANSWERED, null, details, Instant.now());
    }

    // Admins.

    @EventListener
    void adminChanged(AdminChanged event) {
        AppUser actor = signedIn();
        Map<String, Object> details = new HashMap<>(event.details());
        details.put("change", event.change());
        details.put("by", actor == null ? null : nameOf(actor));
        toAdmins(event.organizationId(), actor == null ? null : actor.getId(), NoticeKind.ADMIN_CHANGE, details);
    }

    @EventListener
    void claimSent(ClaimSent event) {
        AppUser claimant = accounts.usersById(List.of(event.claimantId())).get(event.claimantId());
        Map<String, Object> details = new HashMap<>();
        details.put("riotId", event.riotId());
        details.put("by", claimant == null ? null : nameOf(claimant));
        toAdmins(event.organizationId(), event.claimantId(), NoticeKind.CLAIM_RECEIVED, details);
    }

    @EventListener
    void statusChanged(OrganizationStatusChanged event) {
        toAdmins(
                event.organizationId(),
                null,
                event.suspended() ? NoticeKind.ORGANIZATION_SUSPENDED : NoticeKind.ORGANIZATION_REACTIVATED,
                Map.of());
    }

    /** Every night, forgets the notices read long ago. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Paris")
    public void forgetOldNotices() {
        store.forgetReadBefore(Instant.now().minus(KEEP_READ));
    }

    private void toAdmins(UUID organizationId, UUID except, NoticeKind kind, Map<String, Object> details) {
        members.findByOrganizationIdOrderByJoinedAt(organizationId).stream()
                .filter(member -> member.getRole() == MemberRole.ADMIN)
                .map(OrganizationMember::getUserId)
                .filter(userId -> !userId.equals(except))
                .forEach(userId -> store.add(userId, organizationId, kind, null, details, Instant.now()));
    }

    /** The accounts linked to these players; players without one are left out. */
    private List<UUID> usersOf(UUID organizationId, Collection<UUID> playerIds) {
        Map<UUID, UUID> userByPlayer = members.findByOrganizationIdAndPlayerIdNotNull(organizationId).stream()
                .collect(Collectors.toMap(OrganizationMember::getPlayerId, OrganizationMember::getUserId));
        return playerIds.stream()
                .map(userByPlayer::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Map<String, Object> matchDetails(Match match) {
        Map<String, Object> details = new HashMap<>();
        details.put("matchId", match.getId());
        details.put("season", match.getSeason().getName());
        details.put("seasonNumber", match.getSeason().getNumber());
        details.put("match", match.getMatchNumber());
        details.put(
                "date",
                match.getMatchDate() == null ? null : match.getMatchDate().toString());
        details.put(
                "startTime",
                match.getStartTime() == null ? null : match.getStartTime().toString());
        details.put("location", match.getLocation());
        details.put(
                "replyBy",
                match.getReplyBy() == null ? null : match.getReplyBy().toString());
        return details;
    }

    /** The admin whose request made the change, while one is signed in. */
    private AppUser signedIn() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
            return null;
        }
        return accounts.user(user.getSubject()).orElse(null);
    }

    private static String nameOf(AppUser user) {
        return user.getDisplayName() == null ? user.getEmail() : user.getDisplayName();
    }

    /** @param unread how many shown notices aren't read yet */
    public record Notices(int unread, List<Notice> notices) {}
}
