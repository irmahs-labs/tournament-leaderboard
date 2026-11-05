package dev.irmahs.leaderboard.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(VersionController.class)
class VersionControllerWithoutBuildInfoTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void reportsUnknownWhenTheBuildDidNotRecordAVersion() {
        assertThat(mvc.get().uri("/api/version"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.version")
                .isEqualTo(VersionController.UNKNOWN);
    }
}
