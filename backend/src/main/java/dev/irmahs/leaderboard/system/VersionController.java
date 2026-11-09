package dev.irmahs.leaderboard.system;

import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the Angular app which version is deployed, so a tab left open over a deploy can offer
 * to reload.
 */
@RestController
public class VersionController {

    /** Version reported when the build didn't produce build-info, such as some IDE runs. */
    static final String UNKNOWN = "unknown";

    private final VersionInfo info;

    public VersionController(Optional<BuildProperties> buildProperties) {
        this.info = buildProperties
                .map(build -> new VersionInfo(build.getVersion(), build.getTime()))
                .orElse(new VersionInfo(UNKNOWN, null));
    }

    @GetMapping("/api/version")
    public VersionInfo version() {
        return info;
    }

    public record VersionInfo(String version, Instant builtAt) {}
}
