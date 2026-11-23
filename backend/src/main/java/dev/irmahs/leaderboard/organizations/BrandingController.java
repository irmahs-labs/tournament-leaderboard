package dev.irmahs.leaderboard.organizations;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The admin Branding screen. Admins of the organisation only; each change answers the new view. */
@RestController
@RequestMapping("/api/orgs/{slug}/admin")
@PreAuthorize("@orgAccess.isAdmin(#slug, authentication)")
public class BrandingController {

    private final BrandingService branding;

    BrandingController(BrandingService branding) {
        this.branding = branding;
    }

    @PutMapping("/branding")
    public OrganizationView rebrand(@PathVariable String slug, @RequestBody Branding input) {
        return branding.rebrand(slug, input);
    }

    /** The image as the request's body, whatever its declared type: its bytes say what it is. */
    @PutMapping("/logo")
    public OrganizationView replaceLogo(@PathVariable String slug, @RequestBody byte[] image) {
        return branding.replaceLogo(slug, image);
    }

    @DeleteMapping("/logo")
    public OrganizationView removeLogo(@PathVariable String slug) {
        return branding.removeLogo(slug);
    }
}
