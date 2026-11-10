package dev.irmahs.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.irmahs.leaderboard.matches.Game;
import dev.irmahs.leaderboard.matches.Match;
import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationStatus;
import dev.irmahs.leaderboard.players.Player;
import dev.irmahs.leaderboard.ranks.Rank;
import dev.irmahs.leaderboard.seasons.BonusAward;
import dev.irmahs.leaderboard.seasons.Season;
import dev.irmahs.leaderboard.seasons.Season.GameRange;
import dev.irmahs.leaderboard.seasons.SeasonBonus;
import dev.irmahs.leaderboard.seasons.Stat;
import dev.irmahs.leaderboard.seasons.StatRule;
import dev.irmahs.leaderboard.stats.GameStat;
import dev.irmahs.leaderboard.stats.StatLine;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the real Flyway migrations on Postgres 17, then saves and reloads a tournament through
 * the entities. Starting at all proves the entities match the tables (ddl-auto: validate). Each
 * test rolls back.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class SchemaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    EntityManager em;

    @Test
    void savesAndReloadsATournament() {
        Organization alsace = save(new Organization("alsace-arena", "Alsace Arena", "Spike Tournament"));
        Rank gold = save(new Rank("Gold 2", 11, "#ECB940"));
        Player kestrel = save(new Player(alsace.getId(), "Kestrel#EUW", "kestrel", gold));

        Season season = new Season(alsace.getId(), 3, "Season 3", 8, new GameRange(1, 5), 50);
        season.setDates(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 18));
        season.setStatRule(Stat.KILLS, new StatRule(true, 1));
        season.setStatRule(Stat.DEATHS, new StatRule(true, -1));
        season.setStatRule(Stat.HEADSHOT_PCT, new StatRule(false, 0));
        SeasonBonus firstBloods = season.addBonus("FB", "Most first bloods", BonusAward.HIGHEST, Stat.FIRST_BLOODS, 10);
        season.addBonus("MVP", "Most valuable player", BonusAward.MANUAL, null, 25);
        save(season);

        Match match = save(new Match(season, 1, LocalDate.of(2026, 10, 2)));
        Game game = save(new Game(match, 2));
        StatLine line = new StatLine(
                240,
                231,
                new BigDecimal("152.3"),
                new BigDecimal("-12.5"),
                new BigDecimal("74.00"),
                18,
                12,
                5,
                60,
                3,
                2,
                1,
                new BigDecimal("27.50"),
                null);
        GameStat stat = new GameStat(game, kestrel, line, true);
        stat.award(firstBloods);
        save(stat);

        em.flush();
        em.clear();

        GameStat reloaded = em.find(GameStat.class, stat.getId());
        assertThat(reloaded.getLine()).isEqualTo(line);
        assertThat(reloaded.isWin()).isTrue();
        assertThat(reloaded.getBonuses()).extracting(SeasonBonus::getCode).containsExactly("FB");

        Player player = reloaded.getPlayer();
        assertThat(player.getId()).isEqualTo(kestrel.getId());
        assertThat(player.getOrganizationId()).isEqualTo(alsace.getId());
        assertThat(player.getRiotId()).isEqualTo("Kestrel#EUW");
        assertThat(player.getDiscordUsername()).isEqualTo("kestrel");
        Rank rank = player.getCurrentRank();
        assertThat(rank.getId()).isEqualTo(gold.getId());
        assertThat(rank.getRankName()).isEqualTo("Gold 2");
        assertThat(rank.getRankValue()).isEqualTo(11);
        assertThat(rank.getColor()).isEqualTo("#ECB940");

        Game reloadedGame = reloaded.getGame();
        assertThat(reloadedGame.getId()).isEqualTo(game.getId());
        assertThat(reloadedGame.getGameNumber()).isEqualTo(2);
        Match reloadedMatch = reloadedGame.getMatch();
        assertThat(reloadedMatch.getId()).isEqualTo(match.getId());
        assertThat(reloadedMatch.getOrganizationId()).isEqualTo(alsace.getId());
        assertThat(reloadedMatch.getMatchNumber()).isEqualTo(1);
        assertThat(reloadedMatch.getMatchDate()).isEqualTo(LocalDate.of(2026, 10, 2));

        Season reloadedSeason = reloadedMatch.getSeason();
        assertThat(reloadedSeason.getId()).isEqualTo(season.getId());
        assertThat(reloadedSeason.getOrganizationId()).isEqualTo(alsace.getId());
        assertThat(reloadedSeason.getNumber()).isEqualTo(3);
        assertThat(reloadedSeason.getName()).isEqualTo("Season 3");
        assertThat(reloadedSeason.getStartsOn()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(reloadedSeason.getEndsOn()).isEqualTo(LocalDate.of(2026, 12, 18));
        assertThat(reloadedSeason.getMatchCount()).isEqualTo(8);
        assertThat(reloadedSeason.getGames()).isEqualTo(new GameRange(1, 5));
        assertThat(reloadedSeason.getPointsPerWin()).isEqualTo(50);
        assertThat(reloadedSeason.getFinishedAt()).isNull();
        assertThat(reloadedSeason.getStatRules())
                .containsEntry(Stat.KILLS, new StatRule(true, 1))
                .containsEntry(Stat.DEATHS, new StatRule(true, -1))
                .containsEntry(Stat.HEADSHOT_PCT, new StatRule(false, 0))
                .hasSize(3);

        assertThat(reloadedSeason.getBonuses()).extracting(SeasonBonus::getCode).containsExactly("FB", "MVP");
        SeasonBonus bonus = reloadedSeason.getBonuses().getFirst();
        assertThat(bonus.getId()).isEqualTo(firstBloods.getId());
        assertThat(bonus.getSeason()).isSameAs(reloadedSeason);
        assertThat(bonus.getLabel()).isEqualTo("Most first bloods");
        assertThat(bonus.getAward()).isEqualTo(BonusAward.HIGHEST);
        assertThat(bonus.getStat()).isEqualTo(Stat.FIRST_BLOODS);
        assertThat(bonus.getPoints()).isEqualTo(10);
        assertThat(bonus.getPosition()).isEqualTo(1);
    }

    @Test
    void aStatLineCanBeEmpty() {
        UUID alsace = organisation("alsace-arena");
        Player kestrel = save(new Player(alsace, "Kestrel#EUW", null, null));
        Game game = save(new Game(save(new Match(save(season(alsace, 1)), 1, null)), 1));
        StatLine empty =
                new StatLine(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        GameStat stat = save(new GameStat(game, kestrel, empty, false));
        em.flush();
        em.clear();

        GameStat reloaded = em.find(GameStat.class, stat.getId());
        assertThat(reloaded.getLine()).isEqualTo(empty);
        assertThat(reloaded.isWin()).isFalse();
        assertThat(reloaded.getBonuses()).isEmpty();
    }

    @Test
    void anOrganisationStartsActiveWithTheDefaults() {
        Organization alsace = save(new Organization("alsace-arena", "Alsace Arena", "Spike Tournament"));
        em.flush();
        em.clear();

        Organization reloaded = em.find(Organization.class, alsace.getId());
        assertThat(reloaded.getSlug()).isEqualTo("alsace-arena");
        assertThat(reloaded.getName()).isEqualTo("Alsace Arena");
        assertThat(reloaded.getTournamentName()).isEqualTo("Spike Tournament");
        assertThat(reloaded.getDescription()).isNull();
        assertThat(reloaded.getDefaultLocale()).isEqualTo("en");
        assertThat(reloaded.getAccentColor()).isNull();
        assertThat(reloaded.getPlayersPerMatch()).isEqualTo(10);
        assertThat(reloaded.getLastActivityAt()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void twoOrganisationsEachHaveTheirOwnSeasonOne() {
        UUID alsace = organisation("alsace-arena");
        UUID lyon = organisation("lyon-game-bar");

        save(season(alsace, 1));
        save(season(lyon, 1));
        em.flush();

        assertThat(count("seasons")).isEqualTo(2);
    }

    @Test
    void refusesTheSameSeasonNumberTwiceInOneOrganisation() {
        UUID alsace = organisation("alsace-arena");
        save(season(alsace, 1));

        assertRefused(() -> save(season(alsace, 1)), "seasons_organization_id_number_key");
    }

    @Test
    void theSameRiotIdCanPlayAtTwoOrganisations() {
        save(new Player(organisation("alsace-arena"), "Kestrel#EUW", null, null));
        save(new Player(organisation("lyon-game-bar"), "Kestrel#EUW", null, null));
        em.flush();

        assertThat(count("players")).isEqualTo(2);
    }

    @Test
    void refusesTheSameRiotIdTwiceInOneOrganisationWhateverItsCase() {
        UUID alsace = organisation("alsace-arena");
        save(new Player(alsace, "Kestrel#EUW", null, null));

        assertRefused(() -> save(new Player(alsace, "kestrel#euw", null, null)), "players_org_riot_id");
    }

    @Test
    void refusesAStatEffectOtherThanAddSubtractOrNotCounted() {
        Season season = season(organisation("alsace-arena"), 1);
        season.setStatRule(Stat.KILLS, new StatRule(true, 2));

        assertRefused(() -> save(season), "season_stat_rules_effect_check");
    }

    @Test
    void refusesAManualBonusThatComparesAStat() {
        Season season = season(organisation("alsace-arena"), 1);
        season.addBonus("MVP", "Most valuable player", BonusAward.MANUAL, Stat.KILLS, 25);

        assertRefused(() -> save(season), "season_bonuses_check");
    }

    @Test
    void refusesASecondOwnerOfAnOrganisation() {
        UUID alsace = organisation("alsace-arena");
        addOwner(alsace, "google-1");

        assertRefused(() -> addOwner(alsace, "google-2"), "one_owner_per_organization");
    }

    /** The database must refuse the change, naming the constraint that stopped it. */
    private void assertRefused(Runnable change, String constraint) {
        assertThatThrownBy(() -> {
                    change.run();
                    em.flush();
                })
                .hasStackTraceContaining(constraint);
    }

    private <T> T save(T entity) {
        em.persist(entity);
        return entity;
    }

    private UUID organisation(String slug) {
        return save(new Organization(slug, slug, null)).getId();
    }

    private static Season season(UUID organizationId, int number) {
        return new Season(organizationId, number, "Season " + number, 8, new GameRange(1, 5), 50);
    }

    private void addOwner(UUID organizationId, String googleSub) {
        em.flush();
        UUID userId = (UUID) em.createNativeQuery(
                        "INSERT INTO app_users (google_sub, email) VALUES (:sub, :email) RETURNING id", UUID.class)
                .setParameter("sub", googleSub)
                .setParameter("email", googleSub + "@example.com")
                .getSingleResult();
        em.createNativeQuery("INSERT INTO organization_members (user_id, organization_id, role, is_owner)"
                        + " VALUES (:user, :organization, 'ADMIN', true)")
                .setParameter("user", userId)
                .setParameter("organization", organizationId)
                .executeUpdate();
    }

    private long count(String table) {
        return (Long) em.createNativeQuery("SELECT count(*) FROM " + table, Long.class)
                .getSingleResult();
    }
}
