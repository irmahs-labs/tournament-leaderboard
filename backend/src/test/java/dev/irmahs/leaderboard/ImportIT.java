package dev.irmahs.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.scoring.ScoringService;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.stats.GameStat;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Runs deploy/import/import.sql with psql on a sample export, against the real schema, then
 * checks that the points equal the live site's: the sample's expected totals were worked out
 * with the formula in legacy/src/constants/scoring.ts.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ImportIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String KESTREL = "11111111-1111-4111-8111-111111111111";
    static final String WREN = "22222222-2222-4222-8222-222222222222";
    static final String HERON = "33333333-3333-4333-8333-333333333333";

    static final String RANKS = """
            id,rank_name,rank_value
            3,Silver 1,7
            11,Gold 2,11
            """;
    static final String PLAYERS = """
            id,username,current_rank_id
            %s,Kestrel#EUW,11
            %s,Wren#EUW,3
            %s,Heron#EUW,
            """.formatted(KESTREL, WREN, HERON);
    static final String MATCHES = """
            id,match_number,match_date,match_season
            7,1,2026-03-05,1
            8,2,2026-03-19,1
            12,1,2026-09-10,2
            """;
    static final String GAMES = """
            id,match_id,game_number
            21,7,1
            22,7,2
            30,8,1
            40,12,1
            """;
    static final String GAME_STATS_HEADER =
            "id,game_id,player_id,acs,kills,deaths,assists,econ_rating,first_bloods,plants,"
                    + "defuses,win,bonus_fb,bonus_death,bonus_assist,bonus_plant,bonus_defuse\n";
    static final String GAME_STATS = GAME_STATS_HEADER + """
            101,21,%1$s,240,18,12,5,60,3,2,1,t,t,f,f,t,f
            102,21,%2$s,180,12,9,8,40,1,0,1,f,f,t,t,f,t
            103,22,%1$s,200,15,14,3,55,2,1,0,f,t,f,f,t,f
            104,22,%3$s,150,9,10,6,35,1,0,0,t,f,t,t,f,f
            105,30,%2$s,210,16,8,4,50,2,1,2,t,t,t,f,t,t
            106,40,%1$s,190,14,11,7,45,0,0,0,t,f,t,t,f,f
            """.formatted(KESTREL, WREN, HERON);

    static final Map<String, String> SAMPLE = Map.of(
            "ranks.csv", RANKS,
            "players.csv", PLAYERS,
            "matches.csv", MATCHES,
            "games.csv", GAMES,
            "game_stats.csv", GAME_STATS);

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EntityManager em;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    ScoringService scoring;

    @BeforeAll
    static void copyTheScript() {
        postgres.copyFileToContainer(MountableFile.forHostPath("../deploy/import/import.sql"), "/import/import.sql");
    }

    @Test
    void importsTheSampleWithTheLiveSitesTotals() throws Exception {
        assertThat(runImport(SAMPLE).getExitCode()).isZero();

        assertThat(seasonTotals(1))
                .containsExactlyEntriesOf(
                        new TreeMap<>(Map.of("Heron#EUW", 261L, "Kestrel#EUW", 669L, "Wren#EUW", 630L)));
        assertThat(seasonTotals(2)).containsExactlyEntriesOf(Map.of("Kestrel#EUW", 315L));
        assertImportedCounts();
    }

    @Test
    void keepsTheIdsAndMapsEachColumn() throws Exception {
        assertThat(runImport(SAMPLE).getExitCode()).isZero();

        assertThat(jdbc.sql("""
                        SELECT s.number, m.match_number, m.match_date FROM matches m
                        JOIN seasons s ON s.id = m.season_id WHERE m.id = 8""")
                        .query((rs, row) -> List.of(rs.getInt(1), rs.getInt(2), rs.getObject(3, LocalDate.class)))
                        .single())
                .containsExactly(1, 2, LocalDate.of(2026, 3, 19));
        assertThat(jdbc.sql("SELECT match_id, game_number FROM games WHERE id = 22")
                        .query((rs, row) -> List.of(rs.getInt(1), rs.getInt(2)))
                        .single())
                .containsExactly(7, 2);
        assertThat(jdbc.sql("SELECT performance_score, acs, headshot_pct FROM game_stats WHERE id = 101")
                        .query((rs, row) -> new Object[] {rs.getObject(1), rs.getObject(2), rs.getObject(3)})
                        .single())
                .containsExactly(240, null, null);
        assertThat(jdbc.sql("SELECT riot_id, current_rank_id FROM players WHERE id = :id::uuid")
                        .param("id", KESTREL)
                        .query((rs, row) -> List.of(rs.getString(1), rs.getInt(2)))
                        .single())
                .containsExactly("Kestrel#EUW", 11);
        assertThat(jdbc.sql("SELECT discord_username FROM players WHERE id = :id::uuid")
                        .param("id", KESTREL)
                        .query((rs, row) -> rs.getString(1) == null)
                        .single())
                .isTrue();

        assertThat(jdbc.sql("""
                        SELECT r.stat || ' ' || r.recorded || ' ' || r.effect FROM season_stat_rules r
                        JOIN seasons s ON s.id = r.season_id
                        WHERE s.number = 1 AND r.stat IN ('PERFORMANCE_SCORE', 'DEATHS', 'ACS', 'KAST_PCT')
                        ORDER BY r.stat""").query(String.class).list())
                .containsExactly("ACS false 0", "DEATHS true -1", "KAST_PCT false 0", "PERFORMANCE_SCORE true 1");
        assertThat(jdbc.sql("""
                        SELECT string_agg(b.code, ',' ORDER BY b.code) FROM game_stat_bonuses a
                        JOIN season_bonuses b ON b.id = a.bonus_id WHERE a.game_stat_id = 105""").query(String.class).single()).isEqualTo("FB,LD,MD,MP");
        assertThat(jdbc.sql("""
                        SELECT name, starts_on, match_count, min_games, max_games, points_per_win
                        FROM seasons WHERE number = 2""")
                        .query((rs, row) -> List.of(
                                rs.getString(1),
                                rs.getObject(2, LocalDate.class),
                                rs.getInt(3),
                                rs.getInt(4),
                                rs.getInt(5),
                                rs.getInt(6)))
                        .single())
                .containsExactly("Season 2", LocalDate.of(2026, 9, 10), 8, 1, 5, 50);
        assertThat(jdbc.sql("SELECT last_activity_at::date FROM organizations WHERE slug = 'alsace-arena'")
                        .query(LocalDate.class)
                        .single())
                .isEqualTo(LocalDate.of(2026, 9, 10));
    }

    @Test
    void movesTheSequencesPastTheImportedIds() throws Exception {
        assertThat(runImport(SAMPLE).getExitCode()).isZero();

        assertThat(nextId("matches")).isGreaterThan(12);
        assertThat(nextId("games")).isGreaterThan(40);
        assertThat(nextId("game_stats")).isGreaterThan(106);
        assertThat(nextId("ranks")).isGreaterThan(11);
    }

    @Test
    void runningItTwiceGivesTheSameResult() throws Exception {
        assertThat(runImport(SAMPLE).getExitCode()).isZero();
        assertThat(runImport(SAMPLE).getExitCode()).isZero();

        assertImportedCounts();
        assertThat(seasonTotals(1)).containsEntry("Kestrel#EUW", 669L);
    }

    @Test
    void importsNothingWhenTheExportHasRowsTheSchemaRefuses() throws Exception {
        Map<String, Long> before = counts();
        Map<String, String> broken = new TreeMap<>(SAMPLE);
        broken.put("game_stats.csv", GAME_STATS + "107,21,%s,100,5,5,5,20,0,0,0,f,f,f,f,f,f\n".formatted(KESTREL));
        broken.put("matches.csv", MATCHES + "13,1,2026-09-24,\n");

        ExecResult result = runImport(broken);

        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStderr())
                .contains("nothing was imported")
                .contains("game 21 has player " + KESTREL + " more than once (stat ids 101, 107)")
                .contains("match 13 has no season");
        assertThat(counts()).isEqualTo(before);
    }

    private ExecResult runImport(Map<String, String> files) throws Exception {
        files.forEach(
                (name, content) -> postgres.copyFileToContainer(Transferable.of(content), "/import/data/" + name));
        return postgres.execInContainer(
                "sh",
                "-c",
                "cd /import/data && psql -U " + postgres.getUsername() + " -d " + postgres.getDatabaseName()
                        + " -f /import/import.sql");
    }

    /** Each player's points for one season of alsace-arena, worked out by {@link ScoringService}. */
    private Map<String, Long> seasonTotals(int number) {
        return tx.execute(status -> {
            List<GameStat> lines = em.createQuery("""
                            select gs from GameStat gs
                            join fetch gs.player
                            join fetch gs.game g join fetch g.match m join fetch m.season s
                            where s.number = :number
                            and s.organizationId = (select o.id from Organization o where o.slug = 'alsace-arena')
                            """, GameStat.class)
                    .setParameter("number", number)
                    .getResultList();
            Map<String, Long> totals = new TreeMap<>();
            for (GameStat line : lines) {
                Season season = line.getGame().getMatch().getSeason();
                long points = scoring.gamePoints(season, line.getLine(), line.getBonuses(), line.isWin());
                totals.merge(line.getPlayer().getRiotId(), points, Long::sum);
            }
            return totals;
        });
    }

    private void assertImportedCounts() {
        assertThat(counts())
                .containsEntry("ranks", 2L)
                .containsEntry("players", 3L)
                .containsEntry("player_aliases", 0L)
                .containsEntry("seasons", 2L)
                .containsEntry("season_stat_rules", 28L)
                .containsEntry("season_bonuses", 10L)
                .containsEntry("matches", 3L)
                .containsEntry("games", 4L)
                .containsEntry("game_stats", 6L)
                .containsEntry("game_stat_bonuses", 15L)
                .containsEntry("organizations", 1L);
    }

    private Map<String, Long> counts() {
        Map<String, Long> counts = new TreeMap<>();
        for (String table : List.of(
                "organizations",
                "ranks",
                "players",
                "player_aliases",
                "seasons",
                "season_stat_rules",
                "season_bonuses",
                "matches",
                "games",
                "game_stats",
                "game_stat_bonuses")) {
            counts.put(
                    table,
                    jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single());
        }
        return counts;
    }

    private long nextId(String table) {
        return jdbc.sql("SELECT nextval(pg_get_serial_sequence(:table, 'id'))")
                .param("table", table)
                .query(Long.class)
                .single();
    }
}
