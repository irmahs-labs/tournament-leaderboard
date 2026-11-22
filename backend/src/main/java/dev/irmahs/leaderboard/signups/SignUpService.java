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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * are open, and withdraws. Admins open or close sign-ups, sign anyone up at any time before the
 * season is over, players without an account too, and withdraw people.
 */
@Service
@Transactional
public class SignUpService {

    private final AccountService accounts;
    private final OrganizationService organizations;
    private final OrganizationMemberRepository members;
    private final PlayerRepository players;
    private final SeasonService seasons;
    private final SeasonRegistrationRepository registrations;

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
    }

    /**
     * The seasons not over yet, newest first, with whether the signed-in person is signed up.
     *
     * @throws ResponseStatusException 403 for someone who isn't a member
     */
    @Transactional(readOnly = true)
    public MySeasons mine(String slug, String googleSub) {
        Organization organization = organizations.requireActive(slug);
        UUID playerId = requireMember(organization, googleSub).getPlayerId();
        Set<Integer> signedUp = playerId == null
                ? Set.of()
                : registrations.findByPlayerIdAndStatus(playerId, RegistrationStatus.REGISTERED).stream()
                        .map(SeasonRegistration::getSeasonId)
                        .collect(Collectors.toSet());
        List<MySeason> open = seasons.newestFirst(organization.getId()).stream()
                .filter(season -> season.getFinishedAt() == null)
                .map(season -> new MySeason(
                        season.getNumber(),
                        season.getName(),
                        season.getStartsOn(),
                        season.getEndsOn(),
                        season.takesSignUps(),
                        signedUp.contains(season.getId())))
                .toList();
        return new MySeasons(playerId != null, open);
    }

    /**
     * Signs the linked player up. Signing up twice changes nothing.
     *
     * @throws ResponseStatusException 403 for someone who isn't a member, 404 for an unknown season,
     *     409 without a player profile or when sign-ups are closed
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
        if (!season.takesSignUps()) {
            throw conflict("Sign-ups for " + season.getName() + " are closed");
        }
        register(organization, season, playerId, registration, null);
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
        withdraw(requireNotOver(slug, number), requirePlayer(organization, googleSub));
        return mine(slug, googleSub);
    }

    /** The admins' view: whether sign-ups are open, and who's signed up, in the order they came. */
    @Transactional(readOnly = true)
    public SignUps signUps(String slug, int number) {
        Organization organization = organizations.requireActive(slug);
        Season season = seasons.require(slug, number);
        List<SeasonRegistration> found =
                registrations.findBySeasonIdAndStatusOrderByRegisteredAt(season.getId(), RegistrationStatus.REGISTERED);
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
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<SignUp> rows = found.stream()
                .map(registration -> {
                    Player player = byId.get(registration.getPlayerId());
                    AppUser admin = registration.getAddedBy() == null ? null : admins.get(registration.getAddedBy());
                    return new SignUp(
                            registration.getPlayerId(),
                            player == null ? null : player.getRiotId(),
                            linked.contains(registration.getPlayerId()),
                            registration.getRegisteredAt(),
                            registration.getAddedBy() != null,
                            admin == null ? null : admin.getDisplayName());
                })
                .toList();
        return new SignUps(
                season.getNumber(), season.getName(), season.getFinishedAt() != null, season.isSignUpOpen(), rows);
    }

    /**
     * Opens or closes sign-ups.
     *
     * @throws ResponseStatusException 400 without the open switch, 404 for an unknown season, 409
     *     once the season is over
     */
    public SignUps setSignUps(String slug, int number, SignUpSettings input) {
        if (input.open() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say whether sign-ups are open");
        }
        requireNotOver(slug, number).setSignUpOpen(input.open());
        return signUps(slug, number);
    }

    /**
     * An admin signs a player up, whether sign-ups are open or not, players without an account too.
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
        SeasonRegistration registration = registrations
                .findBySeasonIdAndPlayerId(season.getId(), playerId)
                .orElse(null);
        if (registration == null || !registration.isRegistered()) {
            register(organization, season, playerId, registration, admin);
        }
        return signUps(slug, number);
    }

    /**
     * An admin withdraws a player: they stop hearing about the season's matches.
     *
     * @throws ResponseStatusException 404 when they aren't signed up, 409 once the season is over
     */
    public SignUps remove(String slug, int number, UUID playerId) {
        withdraw(requireNotOver(slug, number), playerId);
        return signUps(slug, number);
    }

    /** The players signed up for a season, in the order they came. */
    @Transactional(readOnly = true)
    public List<UUID> signedUp(Season season) {
        return registrations
                .findBySeasonIdAndStatusOrderByRegisteredAt(season.getId(), RegistrationStatus.REGISTERED)
                .stream()
                .map(SeasonRegistration::getPlayerId)
                .toList();
    }

    /** The seasons a player is signed up for, by their ids. */
    @Transactional(readOnly = true)
    public Set<Integer> seasonsOf(UUID playerId) {
        return registrations.findByPlayerIdAndStatus(playerId, RegistrationStatus.REGISTERED).stream()
                .map(SeasonRegistration::getSeasonId)
                .collect(Collectors.toSet());
    }

    /** Signs them up, again when they had withdrawn; by themselves when no admin is given. */
    private void register(
            Organization organization, Season season, UUID playerId, SeasonRegistration existing, UUID admin) {
        if (existing != null) {
            existing.register(admin);
            return;
        }
        save(new SeasonRegistration(season.getId(), playerId, organization.getId(), admin));
    }

    private void withdraw(Season season, UUID playerId) {
        registrations
                .findBySeasonIdAndPlayerId(season.getId(), playerId)
                .filter(SeasonRegistration::isRegistered)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Not signed up for " + season.getName()))
                .withdraw();
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
     * @param takesSignUps whether they can sign up themselves
     */
    public record MySeason(
            int number, String name, LocalDate startsOn, LocalDate endsOn, boolean takesSignUps, boolean signedUp) {}

    /** @param player whether they have a player profile here, needed to sign up */
    public record MySeasons(boolean player, List<MySeason> seasons) {}

    /**
     * Someone signed up.
     *
     * @param account whether the player is linked to an account, so can answer matches themselves
     * @param byAdmin whether an admin signed them up
     * @param addedBy that admin's name, while they have an account
     */
    public record SignUp(
            UUID playerId, String riotId, boolean account, Instant registeredAt, boolean byAdmin, String addedBy) {}

    /**
     * A season's sign-ups, in the order they came.
     *
     * @param open whether players can sign up themselves, until the season is over
     */
    public record SignUps(int number, String name, boolean finished, boolean open, List<SignUp> registrations) {}

    public record SignUpSettings(Boolean open) {}
}
