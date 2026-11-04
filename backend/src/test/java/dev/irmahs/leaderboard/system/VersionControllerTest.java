package dev.irmahs.leaderboard.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(VersionController.class)
@Import(VersionControllerTest.Build.class)
class VersionControllerTest {

  @Autowired MockMvcTester mvc;

  @Test
  void reportsTheBuildVersion() {
    assertThat(mvc.get().uri("/api/version"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.version")
        .isEqualTo("1.4.0");
  }

  @TestConfiguration
  static class Build {
    @Bean
    BuildProperties buildProperties() {
      Properties properties = new Properties();
      properties.setProperty("version", "1.4.0");
      properties.setProperty("time", Instant.parse("2026-11-05T10:00:00Z").toString());
      return new BuildProperties(properties);
    }
  }
}
