package dev.irmahs.leaderboard.founding;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.AppUser;
import dev.irmahs.leaderboard.members.OrganizationMember;
import dev.irmahs.leaderboard.members.OrganizationMemberRepository;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Anyone signed in creates an organisation and becomes its owner, up to a few each: the platform
 * stays open without one account filling it with addresses.
 */
@Service
@Transactional
public class FoundingService {

    /** How many organisations one account can own; platform admins have no limit. */
    static final int MAX_OWNED = 3;

    static final int MAX_NAME = 60;

    /** As the database checks it: 3 to 40 lowercase letters, digits and inner hyphens. */
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$");

    /** Addresses that would read as the platform's own. */
    private static final Set<String> RESERVED =
            Set.of("admin", "api", "app", "help", "join", "new", "platform", "settings", "sign-in", "support");

    private final AccountService accounts;
    private final OrganizationService organizations;
    private final OrganizationMemberRepository members;

    FoundingService(AccountService accounts, OrganizationService organizations, OrganizationMemberRepository members) {
        this.accounts = accounts;
        this.organizations = organizations;
        this.members = members;
    }

    /**
     * Creates the organisation, with the person who asked as its owner.
     *
     * @throws ResponseStatusException 400 for a name or address that isn't allowed, 403 past
     *     {@value #MAX_OWNED} organisations owned, 409 when the address is taken
     */
    public Founded found(String googleSub, Founding input) {
        AppUser founder = accounts.user(googleSub)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first"));
        String name = input.name() == null ? "" : input.name().strip();
        String slug = input.slug() == null ? "" : input.slug().strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The name is 1 to 60 characters");
        }
        if (!SLUG.matcher(slug).matches() || slug.contains("--") || RESERVED.contains(slug)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The address is 3 to 40 lowercase letters, digits and hyphens");
        }
        if (!founder.isPlatformAdmin() && members.countByUserIdAndOwnerTrue(founder.getId()) >= MAX_OWNED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "One account can own " + MAX_OWNED + " organisations at most");
        }
        if (organizations.slugTaken(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The address " + slug + " is taken");
        }
        Organization organization;
        try {
            organization = organizations.found(slug, name, founder.getId());
        } catch (DataIntegrityViolationException e) {
            // Someone else took the address in the meantime.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The address " + slug + " is taken");
        }
        members.save(OrganizationMember.owner(founder.getId(), organization.getId()));
        return new Founded(organization.getSlug(), organization.getName());
    }

    /** What the home page's form sends. */
    public record Founding(String name, String slug) {}

    /** The new organisation's address and name. */
    public record Founded(String slug, String name) {}
}
