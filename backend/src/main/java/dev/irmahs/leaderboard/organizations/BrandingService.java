package dev.irmahs.leaderboard.organizations;

import dev.irmahs.leaderboard.events.AdminChanged;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The admin Branding screen: names, language, accent, places per match, social links and logo. */
@Service
@Transactional
public class BrandingService {

    static final int MAX_NAME = 60;
    static final int MAX_DESCRIPTION = 200;
    static final int MAX_LOGO_BYTES = 256 * 1024;

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Set<String> LOCALES = Set.of("en", "fr");
    private static final Pattern SVG_SCRIPTING =
            Pattern.compile("<script|<foreignobject|\\son\\w+\\s*=|javascript:", Pattern.CASE_INSENSITIVE);

    private final OrganizationService organizations;
    private final OrganizationLogoRepository logos;
    private final ApplicationEventPublisher events;

    BrandingService(
            OrganizationService organizations, OrganizationLogoRepository logos, ApplicationEventPublisher events) {
        this.organizations = organizations;
        this.logos = logos;
        this.events = events;
    }

    /** What every page of the organisation shows first. */
    @Transactional(readOnly = true)
    public OrganizationView view(String slug) {
        Organization organization = organizations.requireActive(slug);
        return view(organization);
    }

    /** The organisation the home page features, if any has played a game yet. */
    @Transactional(readOnly = true)
    public Optional<OrganizationView> featured() {
        return organizations.mostRecentlyActive().map(this::view);
    }

    /**
     * Saves the organisation's names, language, accent, places per match and social links.
     *
     * @throws ResponseStatusException 400 naming the first value that isn't allowed
     */
    public OrganizationView rebrand(String slug, Branding input) {
        Organization organization = organizations.requireActive(slug);
        organization.rebrand(checked(input));
        events.publishEvent(new AdminChanged(organization.getId(), "BRANDING_CHANGED", Map.of()));
        return view(organization);
    }

    /**
     * Replaces the logo with a PNG, JPEG, WebP or SVG image, told apart by its first bytes.
     *
     * @throws ResponseStatusException 413 above 256 KB, 415 for any other kind of file
     */
    public OrganizationView replaceLogo(String slug, byte[] image) {
        Organization organization = organizations.requireActive(slug);
        if (image.length > MAX_LOGO_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "A logo can be 256 KB at most");
        }
        String contentType = imageType(image)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A logo is a PNG, JPEG, WebP or SVG image"));
        OrganizationLogo logo =
                logos.findById(organization.getId()).orElseGet(() -> new OrganizationLogo(organization.getId()));
        logo.replace(contentType, image, Instant.now());
        logos.saveAndFlush(logo);
        events.publishEvent(new AdminChanged(organization.getId(), "BRANDING_CHANGED", Map.of()));
        return OrganizationView.of(organization, logo.getUpdatedAt());
    }

    /** Goes back to the initials. */
    public OrganizationView removeLogo(String slug) {
        Organization organization = organizations.requireActive(slug);
        logos.deleteById(organization.getId());
        events.publishEvent(new AdminChanged(organization.getId(), "BRANDING_CHANGED", Map.of()));
        return OrganizationView.of(organization, null);
    }

    /** The logo of an active organisation, if it has one. */
    @Transactional(readOnly = true)
    public Optional<Logo> logo(String slug) {
        Organization organization = organizations.requireActive(slug);
        return logos.findById(organization.getId())
                .map(logo -> new Logo(logo.getContentType(), logo.getBytes(), logo.getUpdatedAt()));
    }

    /** The organisation as its pages show it, with its logo's current address. */
    private OrganizationView view(Organization organization) {
        return OrganizationView.of(
                organization, logos.findUpdatedAt(organization.getId()).orElse(null));
    }

    /** PNG, JPEG, WebP or a scriptless SVG, from the file's content rather than its name. */
    static Optional<String> imageType(byte[] image) {
        if (startsWith(image, 0, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) {
            return Optional.of("image/png");
        }
        if (startsWith(image, 0, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(image, 0, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && startsWith(image, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return Optional.of("image/webp");
        }
        String text = new String(image, StandardCharsets.UTF_8);
        String start = text.stripLeading().replaceFirst("^﻿", "").toLowerCase(Locale.ROOT);
        boolean svg = (start.startsWith("<svg") || start.startsWith("<?xml") || start.startsWith("<!--"))
                && start.contains("<svg");
        return svg && !SVG_SCRIPTING.matcher(text).find() ? Optional.of("image/svg+xml") : Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
        return bytes.length >= offset + prefix.length
                && Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static Branding checked(Branding input) {
        String name = text(input.name(), "The name", MAX_NAME);
        if (name == null) {
            throw invalid("The name can't be empty");
        }
        String locale =
                input.defaultLocale() == null ? "" : input.defaultLocale().strip();
        if (!LOCALES.contains(locale)) {
            throw invalid("The language is en or fr");
        }
        String accent = text(input.accentColor(), "The accent colour", 7);
        if (accent != null && !HEX.matcher(accent).matches()) {
            throw invalid("The accent colour is a #RRGGBB code");
        }
        if (input.playersPerMatch() < 2 || input.playersPerMatch() > 40) {
            throw invalid("A match has 2 to 40 places");
        }
        SocialLinks links = input.socialLinks() == null ? SocialLinks.NONE : input.socialLinks();
        return new Branding(
                name,
                text(input.tournamentName(), "The tournament name", MAX_NAME),
                text(input.description(), "The description", MAX_DESCRIPTION),
                locale,
                accent == null ? null : accent.toUpperCase(Locale.ROOT),
                input.playersPerMatch(),
                new SocialLinks(
                        link(SocialSite.DISCORD, links.discord()),
                        link(SocialSite.INSTAGRAM, links.instagram()),
                        link(SocialSite.TWITCH, links.twitch()),
                        link(SocialSite.YOUTUBE, links.youtube()),
                        link(SocialSite.TIKTOK, links.tiktok()),
                        link(SocialSite.WEBSITE, links.website())));
    }

    /** Trimmed, nothing when empty. */
    private static String text(String value, String what, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > max) {
            throw invalid(what + " is " + max + " characters at most");
        }
        return trimmed;
    }

    private static String link(SocialSite site, String address) {
        try {
            return site.check(address).orElse(null);
        } catch (IllegalArgumentException e) {
            throw invalid("The " + site.name().toLowerCase(Locale.ROOT) + " link " + e.getMessage());
        }
    }

    private static ResponseStatusException invalid(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    /** A logo as it's served. Compared and printed by its content, not by the array's identity. */
    public record Logo(String contentType, byte[] bytes, Instant updatedAt) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Logo logo
                    && contentType.equals(logo.contentType)
                    && Arrays.equals(bytes, logo.bytes)
                    && updatedAt.equals(logo.updatedAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(contentType, Arrays.hashCode(bytes), updatedAt);
        }

        @Override
        public String toString() {
            return "Logo[" + contentType + ", " + bytes.length + " bytes, " + updatedAt + "]";
        }
    }
}
