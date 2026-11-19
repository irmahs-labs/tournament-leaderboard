package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.LYON_ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.PLAYER;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.organizations.Organization;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The Branding screen's API and the logo, on a real Postgres. Each test rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class BrandingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String ORG = "/api/orgs/alsace-arena";
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final String BRANDING = """
            {"name": " Alsace Arena Strasbourg ", "tournamentName": "Spike Tournament",
             "description": "", "defaultLocale": "fr", "accentColor": "#2f5bff", "playersPerMatch": 12,
             "socialLinks": {"discord": "https://discord.gg/alsace", "instagram": "",
                             "twitch": null, "youtube": "https://www.youtube.com/@alsace",
                             "tiktok": null, "website": "https://alsace-arena.fr"}}""";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountService accounts;

    @BeforeEach
    void seed() {
        Organization alsace = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        Organization lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsace);
        em.persist(lyon);
        em.flush();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, OidcLoginRequestPostProcessor who) {
        return call(mvc, method, uri, body, who);
    }

    private MvcTestResult upload(byte[] image, String type) {
        return mvc.put()
                .uri(ORG + "/admin/logo")
                .with(csrf())
                .with(ADMIN)
                .contentType(MediaType.parseMediaType(type))
                .content(image)
                .exchange();
    }

    @Test
    void anOrganisationWithoutBrandingHasNoLinksAndNoLogo() {
        assertThat(send(HttpMethod.GET, ORG, null, null))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"logo": null, "socialLinks": {"discord": null, "instagram": null, "twitch": null,
                         "youtube": null, "tiktok": null, "website": null}}""");
        assertThat(send(HttpMethod.GET, ORG + "/logo", null, null)).hasStatus(404);
    }

    @Test
    void anAdminSavesTheBrandingTidiedUp() {
        assertThat(send(HttpMethod.PUT, ORG + "/admin/branding", BRANDING, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"slug": "alsace-arena", "name": "Alsace Arena Strasbourg", "description": null,
                         "defaultLocale": "fr", "accentColor": "#2F5BFF", "playersPerMatch": 12,
                         "socialLinks": {"discord": "https://discord.gg/alsace", "instagram": null,
                                         "youtube": "https://www.youtube.com/@alsace",
                                         "website": "https://alsace-arena.fr"}}""");
        em.flush();
        em.clear();
        assertThat(send(HttpMethod.GET, ORG, null, null)).bodyJson().isLenientlyEqualTo("""
                        {"name": "Alsace Arena Strasbourg", "playersPerMatch": 12,
                         "socialLinks": {"discord": "https://discord.gg/alsace"}}""");
    }

    @Test
    void onlyItsAdminsChangeTheBranding() {
        assertThat(send(HttpMethod.PUT, ORG + "/admin/branding", BRANDING, null))
                .hasStatus(401);
        assertThat(send(HttpMethod.PUT, ORG + "/admin/branding", BRANDING, PLAYER))
                .hasStatus(403);
        assertThat(send(HttpMethod.PUT, ORG + "/admin/branding", BRANDING, LYON_ADMIN))
                .hasStatus(403);
        assertThat(send(HttpMethod.PUT, ORG + "/admin/branding", BRANDING, NOBODY))
                .hasStatus(403);
        assertThat(send(HttpMethod.DELETE, ORG + "/admin/logo", null, PLAYER)).hasStatus(403);
    }

    static Stream<Arguments> refused() {
        return Stream.of(
                Arguments.of("\"name\": \" \"", "\"name\": \" Alsace Arena Strasbourg \""),
                Arguments.of("\"name\": \"" + "x".repeat(61) + "\"", "\"name\": \" Alsace Arena Strasbourg \""),
                Arguments.of("\"defaultLocale\": \"de\"", "\"defaultLocale\": \"fr\""),
                Arguments.of("\"accentColor\": \"blue\"", "\"accentColor\": \"#2f5bff\""),
                Arguments.of("\"playersPerMatch\": 1", "\"playersPerMatch\": 12"),
                Arguments.of("\"playersPerMatch\": 41", "\"playersPerMatch\": 12"),
                Arguments.of(
                        "\"discord\": \"https://discord.gg.evil.com/x\"", "\"discord\": \"https://discord.gg/alsace\""),
                Arguments.of("\"discord\": \"http://discord.gg/x\"", "\"discord\": \"https://discord.gg/alsace\""),
                Arguments.of("\"website\": \"javascript:alert(1)\"", "\"website\": \"https://alsace-arena.fr\""),
                Arguments.of(
                        "\"website\": \"https://user@alsace-arena.fr\"", "\"website\": \"https://alsace-arena.fr\""),
                Arguments.of(
                        "\"youtube\": \"https://www.instagram.com/alsace\"",
                        "\"youtube\": \"https://www.youtube.com/@alsace\""));
    }

    @ParameterizedTest
    @MethodSource("refused")
    void valuesThatArentAllowedAreRefused(String replacement, String original) {
        assertThat(BRANDING).contains(original);
        String body = BRANDING.replace(original, replacement);

        assertThat(send(HttpMethod.PUT, ORG + "/admin/branding", body, ADMIN)).hasStatus(400);
    }

    @Test
    void aLogoIsServedUnderAnAddressThatChangesWithIt() throws Exception {
        MvcTestResult uploaded = upload(PNG, "image/png");
        assertThat(uploaded).hasStatusOk();
        String logo = JsonPath.read(uploaded.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.logo");
        assertThat(logo).startsWith(ORG + "/logo?v=");

        MvcTestResult served = send(HttpMethod.GET, logo, null, null);
        assertThat(served)
                .hasStatusOk()
                .hasContentType("image/png")
                .hasHeader("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox");
        assertThat(served.getResponse().getHeader("Cache-Control")).contains("max-age=31536000");
        assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(send(HttpMethod.GET, ORG, null, null)).bodyJson().isLenientlyEqualTo("{\"logo\": \"" + logo + "\"}");

        assertThat(send(HttpMethod.DELETE, ORG + "/admin/logo", null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"logo\": null}");
        assertThat(send(HttpMethod.GET, ORG + "/logo", null, null)).hasStatus(404);
    }

    @Test
    void theLogoIsToldApartByItsContent() {
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        byte[] svg =
                "<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);

        assertThat(upload(webp, "application/octet-stream")).hasStatusOk();
        assertThat(send(HttpMethod.GET, ORG + "/logo", null, null)).hasContentType("image/webp");
        assertThat(upload(svg, "image/svg+xml")).hasStatusOk();
        assertThat(send(HttpMethod.GET, ORG + "/logo", null, null)).hasContentType("image/svg+xml");
    }

    @Test
    void otherFilesScriptsAndLargeImagesAreRefused() {
        byte[] scripted =
                "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] large = new byte[256 * 1024 + 1];
        System.arraycopy(PNG, 0, large, 0, PNG.length);

        assertThat(upload("GIF89a".getBytes(StandardCharsets.US_ASCII), "image/gif"))
                .hasStatus(415);
        assertThat(upload(scripted, "image/svg+xml")).hasStatus(415);
        assertThat(upload(large, "image/png")).hasStatus(413);
    }
}
