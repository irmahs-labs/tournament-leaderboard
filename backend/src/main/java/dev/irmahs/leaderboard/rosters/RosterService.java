package dev.irmahs.leaderboard.rosters;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.events.MatchChanged;
import dev.irmahs.leaderboard.matches.GameRepository;
import dev.irmahs.leaderboard.matches.Match;
import dev.irmahs.leaderboard.matches.Match.Plan;
import dev.irmahs.leaderboard.matches.MatchRepository;
import dev.irmahs.leaderboard.matches.MatchStatus;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.players.PlayerRepository;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonService;
import dev.irmahs.leaderboard.signups.SignUpService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The schedule and the match rosters. Admins plan each match of a season and decide who plays;
 * players signed up for the season answer "I'm in" or "Can't make it". "I'm in" takes a place
 * while there is one, and joins the waiting list after that, first come first served. Every change
 * to a roster locks the match's row first, so two players taking the last place are handled one
 * after the other. A locked roster only changes by an admin's hand.
 */
@Service
@Transactional
public class RosterService {

    static final int MIN_PLACES = 2;
    static final int MAX_PLACES = 40;
    static final int MAX_LOCATION = 200;

    private final AccountService accounts;
    private final OrganizationService organizations;
    private final OrganizationMemberRepository members;
    private final PlayerRepository players;
    private final SeasonService seasons;
    private final SignUpService signUps;
    private final MatchRepository matches;
    private final GameRepository games;
    private final AttendanceRepository attendance;
    private final ApplicationEventPublisher events;

    RosterService(
            AccountService accounts,
            OrganizationService organizations,
            OrganizationMemberRepository members,
            PlayerRepository players,
            SeasonService seasons,
            SignUpService signUps,
            MatchRepository matches,
            GameRepository games,
            AttendanceRepository attendance,
            ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.organizations = organizations;
        this.members = members;
        this.players = players;
        this.seasons = seasons;
        this.signUps = signUps;
        this.matches = matches;
        this.games = games;
        this.attendance = attendance;
        this.events = events;
    }

    // Admins: the schedule.

    /** Every match of the season, planned or not, with how full each one is. */
    @Transactional(readOnly = true)
    public Schedule schedule(String slug, int number) {
        Organization organization = organizations.requireActive(slug);
        Season season = seasons.require(slug, number);
        Map<Integer, Match> byNumber = matches.findBySeasonOrderByMatchNumber(season).stream()
                .collect(Collectors.toMap(Match::getMatchNumber, Function.identity()));
        Map<Integer, List<Attendance>> answers = byMatch(byNumber.values());
        List<ScheduledMatch> rows = IntStream.rangeClosed(1, season.getMatchCount())
                .mapToObj(matchNumber -> {
                    Match match = byNumber.get(matchNumber);
                    if (match == null) {
                        return ScheduledMatch.unplanned(matchNumber, organization.getPlayersPerMatch());
                    }
                    List<Attendance> ofMatch = answers.getOrDefault(match.getId(), List.of());
                    return ScheduledMatch.of(
                            match,
                            games.countByMatch(match) > 0,
                            count(ofMatch, AttendanceStatus.CONFIRMED),
                            count(ofMatch, AttendanceStatus.WAITING));
                })
                .toList();
        return new Schedule(
                season.getNumber(),
                season.getName(),
                season.getFinishedAt() != null,
                organization.getPlayersPerMatch(),
                rows);
    }

    /**
     * Plans a match, or changes its plan: day, start time, where, reply-by date and places; or
     * calls it off, or puts it back on.
     *
     * @throws ResponseStatusException 400 for a wrong number or detail, 404 for an unknown season,
     *     409 once the season is over or for fewer places than players already confirmed
     */
    public Schedule plan(String slug, int number, int matchNumber, PlanInput input) {
        Organization organization = organizations.requireActive(slug);
        Season season = requireNotOver(slug, number);
        if (matchNumber < 1 || matchNumber > season.getMatchCount()) {
            throw badRequest(season.getName() + " has matches 1 to " + season.getMatchCount());
        }
        Plan plan = check(input, organization.getPlayersPerMatch());
        Match existing = matches.findBySeasonAndMatchNumber(season, matchNumber)
                .map(found ->
                        matches.lockById(found.getId(), organization.getId()).orElseThrow())
                .orElse(null);
        Match match = existing != null ? existing : matches.save(new Match(season, matchNumber, plan.date()));
        boolean wasOn = existing != null && !existing.isCancelled() && existing.getMatchDate() != null;
        List<Object> whenBefore = List.of(
                String.valueOf(match.getMatchDate()),
                String.valueOf(match.getStartTime()),
                String.valueOf(match.getLocation()));
        requireRoomFor(match, plan.places());
        boolean roomBefore = match.isAutoPromote() && !match.isCancelled();
        int placesBefore = match.getPlaces();
        match.plan(plan);
        if (Boolean.TRUE.equals(input.cancelled())) {
            match.cancel();
        } else if (Boolean.FALSE.equals(input.cancelled())) {
            match.restore();
        }
        if (input.autoPromote() != null) {
            match.setAutoPromote(input.autoPromote());
        }
        // A place added, automatic moves turned back on, or the match back on: places may be free.
        if (plan.places() > placesBefore || !roomBefore) {
            fillFromWaitingList(match);
        }
        matches.flush();
        announce(match, wasOn, whenBefore);
        return schedule(slug, number);
    }

    // Admins: one match's roster.

    /** Who's in, who's waiting, who can't make it, and who signed up but hasn't answered. */
    @Transactional(readOnly = true)
    public Roster roster(String slug, int matchId) {
        Organization organization = organizations.requireActive(slug);
        return rosterOf(requireMatch(organization, matchId));
    }

    /**
     * An admin writes someone's answer down, or clears it: any player of the organisation, players
     * without an account too, and even once the roster is locked.
     *
     * @param status nothing clears their answer
     * @throws ResponseStatusException 404 for an unknown match or player, 409 when the match is full
     *     or called off
     */
    public Roster setAnswer(String slug, String adminSub, int matchId, UUID playerId, AttendanceStatus status) {
        Organization organization = organizations.requireActive(slug);
        Match match = lockMatch(organization, matchId);
        if (match.isCancelled()) {
            throw conflict("This match is called off");
        }
        players.findByIdAndOrganizationId(playerId, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no such player"));
        Attendance answer =
                attendance.findByMatchIdAndPlayerId(match.getId(), playerId).orElse(null);
        boolean wasIn = answer != null && answer.is(AttendanceStatus.CONFIRMED);
        if (status == null) {
            if (answer != null) {
                attendance.delete(answer);
            }
        } else {
            boolean alreadyIn = answer != null && answer.is(AttendanceStatus.CONFIRMED);
            if (status == AttendanceStatus.CONFIRMED && !alreadyIn && isFull(match)) {
                throw conflict("This match is full: add a place, or move someone first");
            }
            record(
                    match,
                    playerId,
                    answer,
                    status,
                    AttendanceSource.ADMIN,
                    requireUser(adminSub).getId());
        }
        attendance.flush();
        if (wasIn && status != AttendanceStatus.CONFIRMED) {
            fillFromWaitingList(match);
        }
        return rosterOf(match);
    }

    /** Locks the roster, so players can't change their answer, or unlocks it. */
    public Roster setLocked(String slug, int matchId, boolean locked) {
        Organization organization = organizations.requireActive(slug);
        Match match = lockMatch(organization, matchId);
        if (locked) {
            match.lock(Instant.now());
        } else {
            match.unlock();
            fillFromWaitingList(match);
        }
        matches.flush();
        return rosterOf(match);
    }

    // Players.

    /**
     * The upcoming matches of the seasons the signed-in player is signed up for, with their answer.
     *
     * @throws ResponseStatusException 403 for someone who isn't a member
     */
    @Transactional(readOnly = true)
    public MyMatches mine(String slug, String googleSub) {
        Organization organization = organizations.requireActive(slug);
        UUID playerId = requireMember(organization, googleSub).getPlayerId();
        if (playerId == null) {
            return new MyMatches(List.of());
        }
        Set<Integer> signedUp = signUps.seasonsOf(playerId);
        List<Season> open = seasons.newestFirst(organization.getId()).stream()
                .filter(season -> season.getFinishedAt() == null && signedUp.contains(season.getId()))
                .toList();
        if (open.isEmpty()) {
            return new MyMatches(List.of());
        }
        List<Match> upcoming = matches.findBySeasonInOrderBySeasonNumberDescMatchNumber(open).stream()
                .filter(match -> games.countByMatch(match) == 0)
                .toList();
        Map<Integer, List<Attendance>> answers = byMatch(upcoming);
        List<MyMatch> rows = upcoming.stream()
                .map(match -> {
                    List<Attendance> ofMatch = answers.getOrDefault(match.getId(), List.of());
                    Attendance mine = ofMatch.stream()
                            .filter(answer -> answer.getPlayerId().equals(playerId))
                            .findFirst()
                            .orElse(null);
                    return new MyMatch(
                            match.getId(),
                            match.getSeason().getNumber(),
                            match.getSeason().getName(),
                            match.getMatchNumber(),
                            match.getMatchDate(),
                            match.getStartTime(),
                            match.getLocation(),
                            match.getReplyBy(),
                            match.getPlaces(),
                            (int) count(ofMatch, AttendanceStatus.CONFIRMED),
                            match.isCancelled(),
                            match.isLocked(),
                            mine == null ? null : mine.getStatus(),
                            mine == null ? null : waitingPosition(ofMatch, mine));
                })
                .toList();
        return new MyMatches(rows);
    }

    /**
     * The signed-in player answers: "I'm in" takes a place while there is one and joins the
     * waiting list after that; "Can't make it" gives their place or waiting spot up.
     *
     * @throws ResponseStatusException 403 for someone who isn't a member, 404 for an unknown match,
     *     409 without a player profile, when they aren't signed up for the season, or when the
     *     match is called off, played or locked
     */
    public MyMatches answer(String slug, String googleSub, int matchId, Answer answer) {
        if (answer == null) {
            throw badRequest("Say whether you're in");
        }
        Organization organization = organizations.requireActive(slug);
        OrganizationMember member = requireMember(organization, googleSub);
        UUID playerId = member.getPlayerId();
        if (playerId == null) {
            throw conflict("Claim or create your player profile first");
        }
        Match match = lockMatch(organization, matchId);
        if (!signUps.seasonsOf(playerId).contains(match.getSeason().getId())) {
            throw conflict("Sign up for " + match.getSeason().getName() + " first");
        }
        if (match.isCancelled()) {
            throw conflict("This match is called off");
        }
        if (match.isLocked()) {
            throw conflict("The roster for this match is locked: ask an admin");
        }
        if (games.countByMatch(match) > 0) {
            throw conflict("This match was already played");
        }
        Attendance current =
                attendance.findByMatchIdAndPlayerId(match.getId(), playerId).orElse(null);
        AttendanceStatus status;
        if (answer == Answer.NO) {
            status = AttendanceStatus.DECLINED;
        } else if (current != null && current.is(AttendanceStatus.CONFIRMED)) {
            status = AttendanceStatus.CONFIRMED;
        } else if (current != null && current.is(AttendanceStatus.WAITING)) {
            status = AttendanceStatus.WAITING;
        } else {
            // Nobody jumps the queue: with players waiting, a new "I'm in" waits too.
            boolean someoneWaiting = attendance.countByMatchIdAndStatus(match.getId(), AttendanceStatus.WAITING) > 0;
            status = isFull(match) || someoneWaiting ? AttendanceStatus.WAITING : AttendanceStatus.CONFIRMED;
        }
        boolean gaveUpPlace = current != null && current.is(AttendanceStatus.CONFIRMED);
        record(match, playerId, current, status, AttendanceSource.APP, member.getUserId());
        attendance.flush();
        if (gaveUpPlace && status != AttendanceStatus.CONFIRMED) {
            fillFromWaitingList(match);
        }
        return mine(slug, googleSub);
    }

    /**
     * Puts the waiting list in the order the admin gives, first to move up first.
     *
     * @throws ResponseStatusException 404 for an unknown match, 409 when the list given isn't
     *     exactly who's waiting, because it changed in the meantime
     */
    public Roster reorder(String slug, int matchId, List<UUID> order) {
        Organization organization = organizations.requireActive(slug);
        Match match = lockMatch(organization, matchId);
        List<Attendance> waiting = attendance.findByMatchId(match.getId()).stream()
                .filter(answer -> answer.is(AttendanceStatus.WAITING))
                .sorted(Comparator.comparing(Attendance::getQueuedAt))
                .toList();
        Map<UUID, Attendance> byPlayer =
                waiting.stream().collect(Collectors.toMap(Attendance::getPlayerId, Function.identity()));
        if (order == null
                || order.size() != waiting.size()
                || !byPlayer.keySet().containsAll(order)) {
            throw conflict("The waiting list changed in the meantime: reload and try again");
        }
        // The same times, handed out in the new order.
        List<Instant> times = waiting.stream().map(Attendance::getQueuedAt).toList();
        for (int i = 0; i < order.size(); i++) {
            byPlayer.get(order.get(i)).requeue(times.get(i));
        }
        attendance.flush();
        return rosterOf(match);
    }

    /**
     * Reminds the players signed up for the season who haven't answered, those with an account.
     *
     * @return how many were reminded
     * @throws ResponseStatusException 404 for an unknown match, 409 when it's called off, locked
     *     or played
     */
    public int remind(String slug, int matchId) {
        Organization organization = organizations.requireActive(slug);
        Match match = requireMatch(organization, matchId);
        if (match.isCancelled() || match.isLocked() || games.countByMatch(match) > 0) {
            throw conflict("Only an open match's players are reminded");
        }
        List<UUID> notAnswered = notAnswered(match);
        events.publishEvent(new Reminded(organization.getId(), match.getId(), notAnswered));
        return notAnswered.size();
    }

    /** The players signed up for the match's season who haven't answered yet. */
    public List<UUID> notAnswered(Match match) {
        Set<UUID> answered = attendance.findByMatchId(match.getId()).stream()
                .map(Attendance::getPlayerId)
                .collect(Collectors.toSet());
        return signUps.signedUp(match.getSeason()).stream()
                .filter(id -> !answered.contains(id))
                .toList();
    }

    // Shared.

    /**
     * Tells the players signed up when a match is first planned with a date, moved, called off or
     * put back on. A change to its places alone isn't news.
     */
    private void announce(Match match, boolean wasOn, List<Object> whenBefore) {
        boolean isOn = !match.isCancelled() && match.getMatchDate() != null;
        String change;
        if (wasOn && !isOn && match.isCancelled()) {
            change = "CANCELLED";
        } else if (!wasOn && isOn) {
            change = "PLANNED";
        } else if (isOn
                && !whenBefore.equals(List.of(
                        String.valueOf(match.getMatchDate()),
                        String.valueOf(match.getStartTime()),
                        String.valueOf(match.getLocation())))) {
            change = "MOVED";
        } else {
            return;
        }
        events.publishEvent(new MatchChanged(match.getOrganizationId(), match.getId(), change));
    }

    /**
     * After a place frees up (a confirmed player leaves, a place is added, automatic moves or the
     * match are back on, the roster is unlocked): while there's a free place, the first player
     * waiting takes it, unless the roster is locked, the match is called off, or the admins decide
     * by hand. Each move is published.
     */
    private void fillFromWaitingList(Match match) {
        if (match.isLocked() || match.isCancelled() || !match.isAutoPromote()) {
            return;
        }
        List<Attendance> waiting = attendance.findByMatchId(match.getId()).stream()
                .filter(answer -> answer.is(AttendanceStatus.WAITING))
                .sorted(Comparator.comparing(Attendance::getQueuedAt))
                .toList();
        long confirmed = attendance.countByMatchIdAndStatus(match.getId(), AttendanceStatus.CONFIRMED);
        for (Attendance next : waiting) {
            if (confirmed >= match.getPlaces()) {
                break;
            }
            next.answer(AttendanceStatus.CONFIRMED, AttendanceSource.WAITING_LIST, null, Instant.now());
            confirmed++;
            events.publishEvent(new MovedUp(match.getOrganizationId(), match.getId(), next.getPlayerId()));
        }
        attendance.flush();
    }

    private Roster rosterOf(Match match) {
        List<Attendance> answers = attendance.findByMatchId(match.getId());
        Set<UUID> answered = answers.stream().map(Attendance::getPlayerId).collect(Collectors.toSet());
        List<UUID> noAnswerIds = signUps.signedUp(match.getSeason()).stream()
                .filter(id -> !answered.contains(id))
                .toList();
        Set<UUID> everyone = new HashSet<>(answered);
        everyone.addAll(noAnswerIds);
        Map<UUID, String> riotIds =
                players.findAllById(everyone).stream().collect(Collectors.toMap(Player::getId, Player::getRiotId));
        Set<UUID> linked = members.findByOrganizationIdAndPlayerIdNotNull(match.getOrganizationId()).stream()
                .map(OrganizationMember::getPlayerId)
                .collect(Collectors.toSet());
        Map<UUID, AppUser> admins = accounts.usersById(answers.stream()
                .map(Attendance::getAnsweredBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        // The waiting list in the order it moves up; the others in the order they answered.
        Map<AttendanceStatus, List<RosterLine>> lists = answers.stream()
                .sorted(Comparator.comparing(
                        answer -> answer.getQueuedAt() != null ? answer.getQueuedAt() : answer.getAnsweredAt()))
                .collect(Collectors.groupingBy(
                        Attendance::getStatus,
                        Collectors.mapping(
                                answer -> {
                                    AppUser by = admins.get(answer.getAnsweredBy());
                                    return new RosterLine(
                                            answer.getPlayerId(),
                                            riotIds.get(answer.getPlayerId()),
                                            linked.contains(answer.getPlayerId()),
                                            answer.getSource(),
                                            answer.getAnsweredAt(),
                                            by == null ? null : by.getDisplayName());
                                },
                                Collectors.toList())));
        List<RosterLine> noAnswer = noAnswerIds.stream()
                .map(id -> new RosterLine(id, riotIds.get(id), linked.contains(id), null, null, null))
                .toList();
        Season season = match.getSeason();
        return new Roster(
                match.getId(),
                season.getNumber(),
                season.getName(),
                match.getMatchNumber(),
                match.getMatchDate(),
                match.getStartTime(),
                match.getLocation(),
                match.getReplyBy(),
                match.getPlaces(),
                match.getStatus(),
                match.isLocked(),
                match.isAutoPromote(),
                games.countByMatch(match) > 0,
                lists.getOrDefault(AttendanceStatus.CONFIRMED, List.of()),
                lists.getOrDefault(AttendanceStatus.WAITING, List.of()),
                lists.getOrDefault(AttendanceStatus.DECLINED, List.of()),
                noAnswer);
    }

    private void record(
            Match match, UUID playerId, Attendance current, AttendanceStatus status, AttendanceSource source, UUID by) {
        Attendance answer =
                current != null ? current : new Attendance(match.getId(), playerId, match.getOrganizationId());
        answer.answer(status, source, by, Instant.now());
        attendance.save(answer);
    }

    private boolean isFull(Match match) {
        return attendance.countByMatchIdAndStatus(match.getId(), AttendanceStatus.CONFIRMED) >= match.getPlaces();
    }

    private void requireRoomFor(Match match, int places) {
        long confirmed = attendance.countByMatchIdAndStatus(match.getId(), AttendanceStatus.CONFIRMED);
        if (confirmed > places) {
            throw conflict(confirmed + " players are already confirmed: move some to the waiting list first");
        }
    }

    private static Integer waitingPosition(List<Attendance> answers, Attendance mine) {
        if (!mine.is(AttendanceStatus.WAITING)) {
            return null;
        }
        return (int) answers.stream()
                        .filter(answer -> answer.is(AttendanceStatus.WAITING))
                        .filter(answer -> answer.getQueuedAt().isBefore(mine.getQueuedAt()))
                        .count()
                + 1;
    }

    /** These matches' answers, by match. */
    private Map<Integer, List<Attendance>> byMatch(Collection<Match> ofMatches) {
        return attendance.findByMatchIdIn(ofMatches.stream().map(Match::getId).toList()).stream()
                .collect(Collectors.groupingBy(Attendance::getMatchId));
    }

    private static long count(List<Attendance> answers, AttendanceStatus status) {
        return answers.stream().filter(answer -> answer.is(status)).count();
    }

    private static Plan check(PlanInput input, int defaultPlaces) {
        int places = input.places() == null ? defaultPlaces : input.places();
        if (places < MIN_PLACES || places > MAX_PLACES) {
            throw badRequest("A match has " + MIN_PLACES + " to " + MAX_PLACES + " places");
        }
        String location = input.location() == null || input.location().isBlank()
                ? null
                : input.location().strip();
        if (location != null && location.length() > MAX_LOCATION) {
            throw badRequest("Where it's played has at most " + MAX_LOCATION + " characters");
        }
        if (input.replyBy() != null && input.date() != null && input.replyBy().isAfter(input.date())) {
            throw badRequest("The reply-by date comes before the match");
        }
        return new Plan(input.date(), input.startTime(), location, input.replyBy(), places);
    }

    private Season requireNotOver(String slug, int number) {
        Season season = seasons.require(slug, number);
        if (season.getFinishedAt() != null) {
            throw conflict(season.getName() + " is over");
        }
        return season;
    }

    private Match requireMatch(Organization organization, int matchId) {
        return matches.findByIdAndOrganizationId(matchId, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no such match"));
    }

    private Match lockMatch(Organization organization, int matchId) {
        return matches.lockById(matchId, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no such match"));
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

    private static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    /** A player's answer in the app. */
    public enum Answer {
        YES,
        NO
    }

    /**
     * What an admin plans for a match.
     *
     * @param places nothing: the organisation's players per match
     * @param cancelled true calls it off, false puts it back on, nothing leaves it as it is
     * @param autoPromote whether the first player waiting takes a place that frees up; nothing
     *     leaves it as it is
     */
    public record PlanInput(
            LocalDate date,
            LocalTime startTime,
            String location,
            LocalDate replyBy,
            Integer places,
            Boolean cancelled,
            Boolean autoPromote) {}

    /**
     * A match on the season's schedule.
     *
     * @param id nothing until it's planned or played
     * @param played whether a game was saved for it
     */
    public record ScheduledMatch(
            Integer id,
            int number,
            LocalDate date,
            LocalTime startTime,
            String location,
            LocalDate replyBy,
            int places,
            MatchStatus status,
            boolean locked,
            boolean played,
            long confirmed,
            long waiting) {

        static ScheduledMatch unplanned(int number, int places) {
            return new ScheduledMatch(null, number, null, null, null, null, places, null, false, false, 0, 0);
        }

        static ScheduledMatch of(Match match, boolean played, long confirmed, long waiting) {
            return new ScheduledMatch(
                    match.getId(),
                    match.getMatchNumber(),
                    match.getMatchDate(),
                    match.getStartTime(),
                    match.getLocation(),
                    match.getReplyBy(),
                    match.getPlaces(),
                    match.getStatus(),
                    match.isLocked(),
                    played,
                    confirmed,
                    waiting);
        }
    }

    /** @param defaultPlaces the organisation's players per match, for a match not planned yet */
    public record Schedule(
            int number, String name, boolean finished, int defaultPlaces, List<ScheduledMatch> matches) {}

    /**
     * A player on a roster.
     *
     * @param account whether they're linked to an account, so can answer themselves
     * @param source how their answer came in; nothing when they haven't answered
     * @param answeredBy the name of who answered, while they have an account
     */
    public record RosterLine(
            UUID playerId,
            String riotId,
            boolean account,
            AttendanceSource source,
            Instant answeredAt,
            String answeredBy) {}

    /**
     * A match's roster.
     *
     * @param waiting in the order they'll move up
     * @param noAnswer signed up for the season but haven't answered
     */
    public record Roster(
            int id,
            int seasonNumber,
            String seasonName,
            int number,
            LocalDate date,
            LocalTime startTime,
            String location,
            LocalDate replyBy,
            int places,
            MatchStatus status,
            boolean locked,
            boolean autoPromote,
            boolean played,
            List<RosterLine> confirmed,
            List<RosterLine> waiting,
            List<RosterLine> declined,
            List<RosterLine> noAnswer) {}

    /**
     * An upcoming match, as a player sees it.
     *
     * @param status their answer; nothing when they haven't answered
     * @param position their place on the waiting list, from 1; nothing when they aren't on it
     */
    public record MyMatch(
            int id,
            int seasonNumber,
            String seasonName,
            int number,
            LocalDate date,
            LocalTime startTime,
            String location,
            LocalDate replyBy,
            int places,
            int confirmed,
            boolean cancelled,
            boolean locked,
            AttendanceStatus status,
            Integer position) {}

    public record MyMatches(List<MyMatch> matches) {}
}
