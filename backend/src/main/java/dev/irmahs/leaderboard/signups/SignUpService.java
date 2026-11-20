package dev.irmahs.leaderboard.signups;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.players.PlayerRepository;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.SeasonService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Season sign-ups. A linked player signs up for a season themselves, with no limit, while sign-ups
 * are open; admins sign anyone up at any time before the season is over, players without an
 * account too, and take people off. Someone an admin took off can't sign up again by themselves.
 */
@Service
@Transactional
public class SignUpService {

    /** Where a sign-up's last day ends. Every organisation is in France for now. */
    static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final AccountService accounts;
    private final OrganizationService organizations;
    private final OrganizationMemberRepository members;
    private final PlayerRepository players;
    private final SeasonService seasons;
    private final SeasonRegistrationRepository registrations;
    private final Clock clock;

    SignUpService(
            AccountService accounts,
            OrganizationService organizations,
            OrganizationMemberRepository members,
            PlayerRepository players,
            SeasonService seasons,
            SeasonRegistrationRepository registrations) {
        this.accounts = accounts;
        this.organizations = organizations;
        this.members = members;
        this.players = players;
        this.seasons = seasons;
        this.registrations = registrations;
        this.clock = Clock.system(ZONE);
    }

    /**
     * The seasons not over yet, newest first, with whether the signed-in person is signed up.
     *
     * @throws ResponseStatusException 403 for someone who isn't a member
     */
    @Transactional(readOnly = true)
    public MySeasons mine(String slug, String googleSub) {
        Organization organization = organizations.requireActive(slug);
        OrganizationMember member = requireMember(organization, googleSub);
        UUID playerId = member.getPlayerId();
        Map<Integer, RegistrationStatus> mine = playerId == null
                ? Map.of()
                : registrations.findByPlayerId(playerId).stream()
                        .collect(Collectors.toMap(SeasonRegistration::getSeasonId, SeasonRegistration::getStatus));
        LocalDate today = LocalDate.now(clock);
        List<MySeason> open = seasons.newestFirst(organization.getId()).stream()
                .filter(season -> season.getFinishedAt() == null)
                .map(season -> new MySeason(
                        season.getNumber(),
                        season.getName(),
                        season.getStartsOn(),
                        season.getEndsOn(),
                        season.takesSignUps(today),
                        season.getSignUpClosesOn(),
                        mine.get(season.getId())))
                .toList();
        return new MySeasons(playerId != null, open);
    }

    /**
     * Signs the linked player up. Signing up twice changes nothing.
     *
     * @throws ResponseStatusException 403 for someone who isn't a member, 404 for an unknown season,
     *     409 without a player profile, when sign-ups are closed, or when an admin took them off
     */
    public MySeasons signUp(String slug, String googleSub, int number) {
        Organization organization = organizations.requireActive(slug);
        UUID playerId = requirePlayer(organization, googleSub);
        Season season = seasons.require(slug, number);
        SeasonRegistration registration = registrations
                .findBySeasonIdAndPlayerId(season.getId(), playerId)
                .orElse(null);
        if (registration != null && registration.isRegistered()) {
            return mine(slug, googleSub);
        }
        if (registration != null && registration.getStatus() == RegistrationStatus.REMOVED) {
            throw conflict("An admin took you off " + season.getName() + ": ask them to sign you up again");
        }
        if (!season.takesSignUps(LocalDate.now(clock))) {
            throw conflict("Sign-ups for " + season.getName() + " are closed");
        }
        if (registration == null) {
            save(new SeasonRegistration(season.getId(), playerId, organization.getId(), null));
        } else {
            registration.register(null);
        }
        return mine(slug, googleSub);
    }

    /**
     * Withdraws the linked player: they stop hearing about the season's matches.
     *
     * @throws ResponseStatusException 403 for someone who isn't a member, 404 when they aren't
     *     signed up, 409 without a player profile or once the season is over
     */
    public MySeasons withdraw(String slug, String googleSub, int number) {
        Organization organization = organizations.requireActive(slug);
        UUID playerId = requirePlayer(organization, googleSub);
        Season season = requireNotOver(slug, number);
        registrations
                .findBySeasonIdAndPlayerId(season.getId(), playerId)
                .filter(SeasonRegistration::isRegistered)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "You aren't signed up for " + season.getName()))
                .withdraw();
        return mine(slug, googleSub);
    }

    /** The admins' view: whether sign-ups are open, and everyone who ever signed up, in order. */
    @Transactional(readOnly = true)
    public SignUps signUps(String slug, int number) {
        Organization organization = organizations.requireActive(slug);
        Season season = seasons.require(slug, number);
        List<SeasonRegistration> found = registrations.findBySeasonIdOrderByRegisteredAt(season.getId());
        Map<UUID, Player> byId =
                players
                        .findAllById(found.stream()
                                .map(SeasonRegistration::getPlayerId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Player::getId, Function.identity()));
        Set<UUID> linked = members.findByOrganizationIdAndPlayerIdNotNull(organization.getId()).stream()
                .map(OrganizationMember::getPlayerId)
                .collect(Collectors.toSet());
        Map<UUID, AppUser> admins = accounts.usersById(found.stream()
                .map(SeasonRegistration::getAddedBy)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet()));
        List<SignUp> rows = found.stream()
                .map(registration -> {
                    Player player = byId.get(registration.getPlayerId());
                    AppUser admin = registration.getAddedBy() == null ? null : admins.get(registration.getAddedBy());
                    return new SignUp(
                            registration.getPlayerId(),
                            player == null ? null : player.getRiotId(),
                            linked.contains(registration.getPlayerId()),
                            registration.getStatus(),
                            registration.getRegisteredAt(),
                            registration.getAddedBy() != null,
                            admin == null ? null : admin.getDisplayName());
                })
                .sorted(Comparator.comparing((SignUp row) -> row.status() != RegistrationStatus.REGISTERED))
                .toList();
        return new SignUps(
                season.getNumber(),
                season.getName(),
                season.getFinishedAt() != null,
                season.isSignUpOpen(),
                season.getSignUpClosesOn(),
                season.takesSignUps(LocalDate.now(clock)),
                rows);
    }

    /**
     * Opens or closes sign-ups, with the last day to sign up, or none.
     *
     * @throws ResponseStatusException 400 without the open switch, 404 for an unknown season, 409
     *     once the season is over
     */
    public SignUps setSignUps(String slug, int number, SignUpSettings input) {
        if (input.open() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say whether sign-ups are open");
        }
        requireNotOver(slug, number).setSignUps(input.open(), input.closesOn());
        return signUps(slug, number);
    }

    /**
     * An admin signs a player up, whether sign-ups are open or not, players without an account and
     * people taken off before too.
     *
     * @throws ResponseStatusException 404 for an unknown season or another organisation's player,
     *     409 once the season is over
     */
    public SignUps add(String slug, String adminSub, int number, UUID playerId) {
        Organization organization = organizations.requireActive(slug);
        Season season = requireNotOver(slug, number);
        players.findByIdAndOrganizationId(playerId, organization.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no such player"));
        UUID admin = requireUser(adminSub).getId();
        registrations
                .findBySeasonIdAndPlayerId(season.getId(), playerId)
                .ifPresentOrElse(
                        registration -> {
                            if (!registration.isRegistered()) {
                                registration.register(admin);
                            }
                        },
                        () -> save(new SeasonRegistration(season.getId(), playerId, organization.getId(), admin)));
        return signUps(slug, number);
    }

    /**
     * Takes a player off the season: they stop hearing about its matches and can't sign up again
     * by themselves.
     *
     * @throws ResponseStatusException 404 when they aren't signed up, 409 once the season is over
     */
    public SignUps remove(String slug, int number, UUID playerId) {
        Season season = requireNotOver(slug, number);
        registrations
                .findBySeasonIdAndPlayerId(season.getId(), playerId)
                .filter(SeasonRegistration::isRegistered)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "This player isn't signed up for " + season.getName()))
                .remove();
        return signUps(slug, number);
    }

    private void save(SeasonRegistration registration) {
        try {
            registrations.saveAndFlush(registration);
        } catch (DataIntegrityViolationException alreadyThere) {
            throw conflict("This player just signed up: reload to see it");
        }
    }

    private Season requireNotOver(String slug, int number) {
        Season season = seasons.require(slug, number);
        if (season.getFinishedAt() != null) {
            throw conflict(season.getName() + " is over");
        }
        return season;
    }

    private UUID requirePlayer(Organization organization, String googleSub) {
        UUID playerId = requireMember(organization, googleSub).getPlayerId();
        if (playerId == null) {
            throw conflict("Claim or create your player profile first");
        }
        return playerId;
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

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    /**
     * A season not over yet, as a member sees it.
     *
     * @param takesSignUps whether they can sign up themselves today
     * @param status theirs; nothing when they never signed up
     */
    public record MySeason(
            int number,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            boolean takesSignUps,
            LocalDate signUpClosesOn,
            RegistrationStatus status) {}

    /** @param player whether they have a player profile here, needed to sign up */
    public record MySeasons(boolean player, List<MySeason> seasons) {}

    /**
     * Someone signed up, or who was.
     *
     * @param account whether the player is linked to an account, so can answer matches themselves
     * @param byAdmin whether an admin signed them up
     * @param addedBy that admin's name, while they have an account
     */
    public record SignUp(
            UUID playerId,
            String riotId,
            boolean account,
            RegistrationStatus status,
            Instant registeredAt,
            boolean byAdmin,
            String addedBy) {}

    /**
     * A season's sign-ups: who's signed up first, in the order they came, then who withdrew or was
     * taken off.
     *
     * @param open the admins' switch
     * @param takesSignUps whether players can sign up themselves today: open, before the last day,
     *     and the season not over
     */
    public record SignUps(
            int number,
            String name,
            boolean finished,
            boolean open,
            LocalDate closesOn,
            boolean takesSignUps,
            List<SignUp> registrations) {}

    public record SignUpSettings(Boolean open, LocalDate closesOn) {}
}
