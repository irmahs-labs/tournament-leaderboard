package dev.irmahs.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts the whole app against a real Postgres, with Flyway running for real. Skipped where
 * Docker isn't available; CI runners have it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    MockMvcTester mvc;

    @Test
    void startsAndReportsHealthy() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void servesTheVersion() {
        assertThat(mvc.get().uri("/api/version"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.version")
                .isNotNull();
    }

    @Test
    void servesTheAngularApp() {
        assertThat(mvc.get().uri("/index.html")).hasStatusOk().bodyText().contains("<app-root");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/o/alsace-arena",
                "/o/alsace-arena/matches",
                "/o/alsace-arena/matches/3/4",
                "/not-found",
                "/sign-in"
            })
    void servesTheAngularAppForItsOwnAddresses(String address) {
        assertThat(mvc.get().uri(address)).hasStatusOk().hasForwardedUrl("/index.html");
    }

    @Test
    void leavesTheApiAlone() {
        assertThat(mvc.get().uri("/api/orgs/nowhere")).hasStatus(404);
    }
}
