package dev.irmahs.leaderboard.system;

import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
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

    private final ObjectProvider<BuildProperties> buildProperties;

    public VersionController(ObjectProvider<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/api/version")
    public VersionInfo version() {
        BuildProperties build = buildProperties.getIfAvailable();
        if (build == null) {
            return new VersionInfo(UNKNOWN, null);
        }
        return new VersionInfo(build.getVersion(), build.getTime());
    }

    public record VersionInfo(String version, Instant builtAt) {}
}
