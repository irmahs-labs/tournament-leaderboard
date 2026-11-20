package dev.irmahs.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.stats.SeasonResultsService;
import dev.irmahs.leaderboard.stats.SeasonResultsService.Standing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Season 8 of Alsace Arena, as exported from Supabase (8 matches, 24 games, 30 players), run
 * through deploy/import/import.sql. Every player's stats, bonuses, wins and points on the new
 * leaderboard must equal the live site's Season 8 table, read from it once into expected.csv.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Season8ImportIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    static final String FOLDER = "import/season-8/";
    static final List<String> EXPORT =
            List.of("ranks.csv", "players.csv", "matches.csv", "games.csv", "game_stats.csv");

    /** The live table's columns after the player: eight stats, then the five bonuses. Wins and points follow. */
    static final List<Stat> STATS = List.of(
            Stat.PERFORMANCE_SCORE,
            Stat.KILLS,
            Stat.DEATHS,
            Stat.ASSISTS,
            Stat.ECON_RATING,
            Stat.FIRST_BLOODS,
            Stat.PLANTS,
            Stat.DEFUSES);

    static final List<String> BONUSES = List.of("FB", "LD", "MA", "MP", "MD");

    @Autowired
    SeasonResultsService results;

    /** Once, with the app started, so Flyway has created the schema. */
    @BeforeAll
    void importSeason8() throws Exception {
        postgres.copyFileToContainer(MountableFile.forHostPath("../deploy/import/import.sql"), "/import/import.sql");
        for (String name : EXPORT) {
            postgres.copyFileToContainer(Transferable.of(resource(name)), "/import/data/" + name);
        }
        var result = postgres.execInContainer(
                "sh",
                "-c",
                "cd /import/data && psql -U " + postgres.getUsername() + " -d " + postgres.getDatabaseName()
                        + " -f /import/import.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    @Test
    void givesEveryPlayerTheLiveSitesStatsBonusesWinsAndPoints() throws Exception {
        Map<String, List<Long>> live = new TreeMap<>();
        resource("expected.csv").lines().skip(1).forEach(line -> {
            String[] cells = line.split(",");
            live.put(
                    cells[0],
                    Arrays.stream(cells, 1, cells.length).map(Long::valueOf).toList());
        });

        Map<String, List<Long>> leaderboard = new TreeMap<>();
        for (Standing standing : results.leaderboard("alsace-arena", 8).standings()) {
            leaderboard.put(standing.riotId(), liveColumnsOf(standing));
        }

        assertThat(leaderboard).hasSize(30).containsExactlyEntriesOf(live);
    }

    @Test
    void ranksYouK0lFirstWith6902Points() {
        Standing first = results.leaderboard("alsace-arena", 8).standings().getFirst();

        assertThat(first.riotId()).isEqualTo("YouK0l");
        assertThat(first.points().longValueExact()).isEqualTo(6902);
    }

    private static List<Long> liveColumnsOf(Standing standing) {
        List<Long> columns = new ArrayList<>();
        for (Stat stat : STATS) {
            columns.add(standing.totals().getOrDefault(stat, 0L));
        }
        for (String code : BONUSES) {
            columns.add((long) standing.bonuses().getOrDefault(code, 0));
        }
        columns.add((long) standing.wins());
        columns.add(standing.points().longValueExact());
        return columns;
    }

    private static String resource(String name) throws IOException {
        return new ClassPathResource(FOLDER + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
