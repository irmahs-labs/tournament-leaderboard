package dev.irmahs.leaderboard;

import static dev.irmahs.leaderboard.AdminTestSupport.ADMIN;
import static dev.irmahs.leaderboard.AdminTestSupport.NOBODY;
import static dev.irmahs.leaderboard.AdminTestSupport.PLAYER;
import static dev.irmahs.leaderboard.AdminTestSupport.as;
import static dev.irmahs.leaderboard.AdminTestSupport.call;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.irmahs.leaderboard.accounts.AccountService;
import dev.irmahs.leaderboard.accounts.GoogleProfile;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Notices, on a real Postgres. Alsace Arena has Season 1 under way; its player account is linked
 * to Kestrel and signed up; Nobody is a second admin. Each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class NoticesIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String ORG = "/api/orgs/alsace-arena";
    static final String NOTICES = "/api/me/notifications";
    static final OidcLoginRequestPostProcessor PLATFORM = as("google-platform");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EntityManager em;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    AccountService accounts;

    @Autowired
    ApplicationContext context;

    UUID kestrel;
    UUID wren;

    @BeforeEach
    void seed() {
        Organization alsace = new Organization("alsace-arena", "Alsace Arena", "Spike Tournament");
        Organization lyon = new Organization("lyon-game-bar", "Lyon Game Bar", null);
        em.persist(alsace);
        em.persist(lyon);
        em.persist(new Season(alsace.getId(), 1, "Season 1", 6, new GameRange(1, 3), 50));
        Player kestrelPlayer = new Player(alsace.getId(), "Kestrel#EUW", null, null);
        Player wrenPlayer = new Player(alsace.getId(), "Wren#EUW", null, null);
        em.persist(kestrelPlayer);
        em.persist(wrenPlayer);
        em.flush();
        kestrel = kestrelPlayer.getId();
        wren = wrenPlayer.getId();
        AdminTestSupport.signInEveryone(accounts, em, jdbc, alsace, lyon);
        accounts.signIn(new GoogleProfile("google-platform", "platform@example.com", "Irma"));
        jdbc.sql("UPDATE organization_members SET player_id = :player WHERE organization_id = :org AND role = 'PLAYER'")
                .param("player", kestrel)
                .param("org", alsace.getId())
                .update();
        jdbc.sql("""
                        INSERT INTO organization_members (user_id, organization_id, role)
                        SELECT id, :org, 'ADMIN' FROM app_users WHERE google_sub = 'google-nobody'""").param("org", alsace.getId()).update();
        em.clear();
        send(HttpMethod.POST, ORG + "/seasons/1/registration", null, PLAYER);
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, OidcLoginRequestPostProcessor who) {
        return call(mvc, method, uri, body, who);
    }

    private List<String> kinds(OidcLoginRequestPostProcessor who) throws Exception {
        String body = send(HttpMethod.GET, NOTICES, null, who).getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.notices[*].kind");
    }

    /** Ten minutes later: notices about matches are shown. */
    private void later() {
        jdbc.sql("UPDATE notifications SET shown_from = now() - interval '1 minute' WHERE shown_from > now()")
                .update();
    }

    private int plan(String body) throws Exception {
        String schedule = send(HttpMethod.PUT, ORG + "/admin/seasons/1/schedule/1", body, ADMIN)
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(schedule, "$.matches[0].id");
    }

    @Test
    void anotherAdminHearsWhatAnAdminChangedButNotTheAdminThemselves() throws Exception {
        assertThat(send(
                        HttpMethod.PUT,
                        ORG + "/admin/seasons/1/matches/1/games/1",
                        "{\"lines\": [{\"playerId\": \"" + kestrel + "\", \"stats\": {}}]}",
                        ADMIN))
                .hasStatusOk();

        assertThat(send(HttpMethod.GET, NOTICES, null, NOBODY))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"unread": 1, "notices": [{"kind": "ADMIN_CHANGE", "organizationSlug": "alsace-arena",
                          "details": {"change": "GAME_SAVED", "by": "Admin", "season": "Season 1", "match": 1},
                          "read": false}]}""");
        assertThat(kinds(ADMIN)).isEmpty();
        assertThat(kinds(PLAYER)).isEmpty();

        assertThat(send(HttpMethod.POST, NOTICES + "/read", null, NOBODY)).hasStatus(204);
        assertThat(send(HttpMethod.GET, NOTICES, null, NOBODY))
                .bodyJson()
                .isLenientlyEqualTo("{\"unread\": 0, \"notices\": [{\"read\": true}]}");
        assertThat(send(HttpMethod.GET, NOTICES, null, null)).hasStatus(401);
    }

    @Test
    void aClaimReachesTheAdminsAndItsAnswerTheClaimant() throws Exception {
        jdbc.sql("UPDATE organization_members SET player_id = NULL WHERE role = 'PLAYER'")
                .update();
        em.clear();
        send(HttpMethod.POST, ORG + "/claim", "{\"playerId\": \"" + wren + "\"}", PLAYER);
        assertThat(kinds(ADMIN)).containsExactly("CLAIM_RECEIVED");

        String queue = send(HttpMethod.GET, ORG + "/admin/claims", null, ADMIN)
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(queue, "$[0].id");
        send(HttpMethod.POST, ORG + "/admin/claims/" + id + "/decline", "{\"reason\": \"Not you\"}", ADMIN);
        assertThat(send(HttpMethod.GET, NOTICES, null, PLAYER)).bodyJson().isLenientlyEqualTo("""
                        {"notices": [{"kind": "CLAIM_ANSWERED",
                                      "details": {"riotId": "Wren#EUW", "accepted": false, "reason": "Not you"}}]}""");
    }

    @Test
    void aMatchPlannedThenMovedIsOneNoticeShownTenMinutesLater() throws Exception {
        int match = plan("{\"date\": \"2026-12-05\"}");
        plan("{\"date\": \"2026-12-06\", \"startTime\": \"20:30\"}");
        assertThat(kinds(PLAYER)).isEmpty();

        later();
        assertThat(send(HttpMethod.GET, NOTICES, null, PLAYER)).bodyJson().isLenientlyEqualTo("""
                        {"unread": 1, "notices": [{"kind": "MATCH_PLANNED",
                          "details": {"matchId": %d, "match": 1, "date": "2026-12-06", "startTime": "20:30"}}]}""".formatted(match));

        plan("{\"date\": \"2026-12-06\", \"startTime\": \"20:30\", \"places\": 8}");
        later();
        assertThat(kinds(PLAYER)).containsExactly("MATCH_PLANNED");

        plan("{\"date\": \"2026-12-06\", \"startTime\": \"20:30\", \"cancelled\": true}");
        later();
        assertThat(kinds(PLAYER)).containsExactly("MATCH_CANCELLED");
        assertThat(kinds(ADMIN)).isEmpty();
    }

    @Test
    void movingUpAndRemindersReachThePlayerAtOnce() throws Exception {
        int match = plan("{\"date\": \"2026-12-05\", \"places\": 2}");
        later();
        send(HttpMethod.POST, NOTICES + "/read", null, PLAYER);

        assertThat(send(HttpMethod.POST, ORG + "/admin/matches/" + match + "/remind", null, ADMIN))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"players\": 1}");
        assertThat(kinds(PLAYER)).first().isEqualTo("REMINDER");

        send(HttpMethod.PUT, ORG + "/admin/matches/" + match + "/roster/" + wren, "{\"status\": \"CONFIRMED\"}", ADMIN);
        Player heron = new Player(
                jdbc.sql("SELECT id FROM organizations WHERE slug = 'alsace-arena'")
                        .query(UUID.class)
                        .single(),
                "Heron#EUW",
                null,
                null);
        em.persist(heron);
        em.flush();
        send(
                HttpMethod.PUT,
                ORG + "/admin/matches/" + match + "/roster/" + heron.getId(),
                "{\"status\": \"CONFIRMED\"}",
                ADMIN);
        send(HttpMethod.PUT, ORG + "/matches/" + match + "/attendance", "{\"answer\": \"YES\"}", PLAYER);
        send(HttpMethod.PUT, ORG + "/admin/matches/" + match + "/roster/" + wren, "{\"status\": \"DECLINED\"}", ADMIN);

        assertThat(kinds(PLAYER)).first().isEqualTo("MOVED_UP");
    }

    @Test
    void playersWhoHaventAnsweredAreRemindedOnceTwoDaysBeforeTheReplyByDate() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Paris"));
        plan("{\"date\": \"%s\", \"replyBy\": \"%s\"}".formatted(today.plusDays(3), today.plusDays(2)));
        Object job = context.getBean("replyByReminders");

        job.getClass().getMethod("remind").invoke(job);
        job.getClass().getMethod("remind").invoke(job);
        later();

        assertThat(kinds(PLAYER)).containsExactlyInAnyOrder("REMINDER", "MATCH_PLANNED");
    }

    @Test
    void theAdminsHearTheirOrganisationWasSuspendedOrReactivated() throws Exception {
        send(
                HttpMethod.PUT,
                "/api/platform/organizations/alsace-arena/status",
                "{\"status\": \"SUSPENDED\"}",
                PLATFORM);
        assertThat(kinds(ADMIN)).containsExactly("ORGANIZATION_SUSPENDED");
        assertThat(kinds(NOBODY)).containsExactly("ORGANIZATION_SUSPENDED");
        assertThat(kinds(PLAYER)).isEmpty();
    }
}
