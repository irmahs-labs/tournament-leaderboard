package dev.irmahs.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import dev.irmahs.leaderboard.tenancy.OrganizationScope;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Row-level security as production has it: the database is set up by deploy/db-init, Flyway
 * runs as the owner, and the app connects as leaderboard_app, the role the policies apply to.
 * The other integration tests connect as the superuser, which row-level security ignores.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RowLevelSecurityIT {

    // Started here rather than by @Container: with one test instance per class, Spring builds
    // the context before JUnit's @BeforeAll callbacks, and it needs the container's address.
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17")
            .withEnv("APP_DB_PASSWORD", "app-password")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../deploy/db-init/01-app-role.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-app-role.sh");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void connectAsTheApp(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", () -> "leaderboard_app");
        properties.add("spring.datasource.password", () -> "app-password");
        properties.add("spring.flyway.user", postgres::getUsername);
        properties.add("spring.flyway.password", postgres::getPassword);
    }

    /** Reads every player and the database's setting, with no filter of its own. */
    @TestConfiguration
    static class UnfilteredReads {

        @Bean
        UnfilteredController unfilteredController(JdbcClient jdbc) {
            return new UnfilteredController(jdbc);
        }
    }

    @RestController
    static class UnfilteredController {

        private final JdbcClient jdbc;

        UnfilteredController(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        @GetMapping("/api/orgs/{slug}/unfiltered")
        @Transactional(readOnly = true)
        public Map<String, Object> unfiltered() {
            return Map.of(
                    "scope",
                            String.valueOf(jdbc.sql("SELECT current_organization_id()")
                                    .query(UUID.class)
                                    .optional()
                                    .orElse(null)),
                    "players",
                            jdbc.sql("SELECT riot_id FROM players ORDER BY riot_id")
                                    .query(String.class)
                                    .list());
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountService accounts;

    UUID alsace;
    UUID lyon;

    @BeforeAll
    void seed() {
        tx.executeWithoutResult(status -> {
            Organization alsaceArena = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
            Organization lyonGameBar = new Organization("lyon-game-bar", "Lyon Game Bar", "Friday Clash");
            em.persist(alsaceArena);
            em.persist(lyonGameBar);
            alsace = alsaceArena.getId();
            lyon = lyonGameBar.getId();
            em.persist(new Player(alsace, "Kestrel#EUW", null, null));
            em.persist(new Player(alsace, "Wren#EUW", null, null));
            em.persist(new Player(lyon, "Osprey#EUW", null, null));
            em.persist(new Season(alsace, 1, "Season 1", 8, new GameRange(1, 5), 50));
            em.persist(new Season(alsace, 2, "Season 2", 8, new GameRange(1, 5), 50));
            em.persist(new Season(lyon, 1, "Season 1", 6, new GameRange(1, 3), 30));
        });
    }

    private List<String> riotIds() {
        return tx.execute(status -> em.createQuery("select p.riotId from Player p order by p.riotId", String.class)
                .getResultList());
    }

    @Test
    void theAppConnectsAsARoleRowLevelSecurityAppliesTo() {
        assertThat(jdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo("leaderboard_app");
        assertThat(jdbc.sql("SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user")
                        .query(Boolean.class)
                        .single())
                .isFalse();
    }

    @Test
    void inAnOrganisationsScopeOnlyItsRowsAreVisible() {
        assertThat(OrganizationScope.call(lyon, this::riotIds)).containsExactly("Osprey#EUW");
        assertThat(OrganizationScope.call(alsace, this::riotIds)).containsExactly("Kestrel#EUW", "Wren#EUW");
        Long lyonSeasons = OrganizationScope.call(
                lyon,
                () -> tx.execute(status -> jdbc.sql("SELECT count(*) FROM seasons")
                        .query(Long.class)
                        .single()));
        assertThat(lyonSeasons).isEqualTo(1);
    }

    @Test
    void withoutAScopeEveryRowIsVisible() {
        assertThat(riotIds()).containsExactly("Kestrel#EUW", "Osprey#EUW", "Wren#EUW");
        assertThat(OrganizationScope.current()).isEmpty();
    }

    @Test
    void anotherOrganisationsRowsCannotBeWrittenInAScope() {
        assertThatThrownBy(() -> OrganizationScope.call(
                        lyon,
                        () -> tx.execute(status -> {
                            em.persist(new Player(alsace, "Intruder#EUW", null, null));
                            em.flush();
                            return null;
                        })))
                .hasStackTraceContaining("row-level security policy");

        int changed = OrganizationScope.call(
                lyon,
                () -> tx.execute(status -> jdbc.sql(
                                "UPDATE players SET discord_username = 'changed' WHERE organization_id = :alsace")
                        .param("alsace", alsace)
                        .update()));
        assertThat(changed).isZero();
        assertThat(riotIds()).doesNotContain("Intruder#EUW");
    }

    @Test
    void aQueryOutsideATransactionIsNotScoped() {
        // The limit of the safety net: the setting is made when a transaction starts. Every
        // repository method and service runs in one; a bare query outside one sees every row.
        assertThat(OrganizationScope.call(
                        lyon,
                        () -> jdbc.sql("SELECT count(*) FROM seasons")
                                .query(Long.class)
                                .single()))
                .isEqualTo(3);
    }

    @Test
    void theScopeEndsWithItsTransaction() {
        OrganizationScope.call(lyon, this::riotIds);

        Optional<UUID> scope = tx.execute(status ->
                jdbc.sql("SELECT current_organization_id()").query(UUID.class).optional());
        assertThat(scope).isEmpty();
    }

    @Test
    void aRequestIsScopedToTheOrganisationInItsAddress() {
        assertThat(mvc.get().uri("/api/orgs/lyon-game-bar/unfiltered"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"scope\": \"" + lyon + "\", \"players\": [\"Osprey#EUW\"]}");
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/unfiltered"))
                .bodyJson()
                .extractingPath("$.players")
                .isEqualTo(List.of("Kestrel#EUW", "Wren#EUW"));
        assertThat(OrganizationScope.current()).isEmpty();
    }

    @Test
    void anUnknownOrganisationGetsNoScopeAndNotFound() {
        assertThat(mvc.get().uri("/api/orgs/nowhere/unfiltered"))
                .bodyJson()
                .extractingPath("$.scope")
                .isEqualTo("null");
        assertThat(mvc.get().uri("/api/orgs/nowhere")).hasStatus(404);
    }

    @Test
    void thePublicApiWorksUnderRowLevelSecurity() {
        assertThat(mvc.get().uri("/api/orgs/lyon-game-bar/seasons"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].name")
                .isEqualTo(List.of("Season 1"));
        assertThat(mvc.get().uri("/api/orgs/alsace-arena/seasons/2/leaderboard"))
                .hasStatusOk();
    }

    @Test
    void signingInWorksUnderRowLevelSecurity() {
        accounts.signIn(new GoogleProfile("google-rls", "owner@example.com", "Owner", null));

        assertThat(mvc.get().uri("/api/me").with(oidcLogin().idToken(token -> token.subject("google-rls"))))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.memberships[*].slug")
                .isEqualTo(List.of("alsace-arena"));
    }
}
