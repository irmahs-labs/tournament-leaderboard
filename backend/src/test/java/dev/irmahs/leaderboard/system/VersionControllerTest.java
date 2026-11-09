package dev.irmahs.leaderboard.system;

import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.system.VersionController.VersionInfo;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class VersionControllerTest {

    @Test
    void reportsTheBuildVersion() {
        Properties properties = new Properties();
        properties.setProperty("version", "1.4.0");
        properties.setProperty("time", "2026-11-05T10:00:00Z");

        VersionController controller = new VersionController(Optional.of(new BuildProperties(properties)));

        assertThat(controller.version()).isEqualTo(new VersionInfo("1.4.0", Instant.parse("2026-11-05T10:00:00Z")));
    }

    @Test
    void reportsUnknownWhenTheBuildDidNotRecordAVersion() {
        VersionController controller = new VersionController(Optional.empty());

        assertThat(controller.version()).isEqualTo(new VersionInfo(VersionController.UNKNOWN, null));
    }
}
