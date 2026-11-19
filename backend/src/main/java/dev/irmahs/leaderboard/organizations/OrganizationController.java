package dev.irmahs.leaderboard.organizations;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** What every page of an organisation needs first, and its logo. */
@RestController
public class OrganizationController {

    /** An SVG opened on its own can't run anything or load anything. */
    static final String LOGO_POLICY = "default-src 'none'; style-src 'unsafe-inline'; sandbox";

    private final BrandingService branding;

    OrganizationController(BrandingService branding) {
        this.branding = branding;
    }

    @GetMapping("/api/orgs/{slug}")
    public OrganizationView organization(@PathVariable String slug) {
        return branding.view(slug);
    }

    /** Its address changes with the logo, so browsers can keep it for a year. */
    @GetMapping("/api/orgs/{slug}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable String slug) {
        return branding.logo(slug)
                .map(logo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(logo.contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                        .lastModified(logo.updatedAt())
                        .header("Content-Security-Policy", LOGO_POLICY)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(logo.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
