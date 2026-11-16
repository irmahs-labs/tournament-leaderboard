package dev.irmahs.leaderboard.players;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.irmahs.leaderboard.players.Player.NewAlias;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerTest {

    private final Player player = new Player(UUID.randomUUID(), "Kestrel#EUW", null, null);

    @Test
    void aNewRiotIdKeepsTheOldOneAsAnAlias() {
        player.rename("Falcon#EUW");

        assertThat(player.getRiotId()).isEqualTo("Falcon#EUW");
        assertThat(player.getAliases())
                .extracting(PlayerAlias::getAlias, PlayerAlias::getKind)
                .containsExactly(tuple("Kestrel#EUW", AliasKind.PREVIOUS_RIOT_ID));
    }

    @Test
    void changingOnlyTheLetterCaseKeepsNoAlias() {
        player.rename("kestrel#euw");

        assertThat(player.getRiotId()).isEqualTo("kestrel#euw");
        assertThat(player.getAliases()).isEmpty();
    }

    @Test
    void goingBackToAPreviousRiotIdDropsItFromTheAliases() {
        player.rename("Falcon#EUW");
        player.rename("Kestrel#EUW");

        assertThat(player.getAliases()).extracting(PlayerAlias::getAlias).containsExactly("Falcon#EUW");
    }

    @Test
    void anOldRiotIdAlreadyKeptIsNotKeptTwice() {
        player.replaceAliases(List.of(new NewAlias("kestrel#euw", AliasKind.OTHER)));
        player.rename("Falcon#EUW");

        assertThat(player.getAliases()).extracting(PlayerAlias::getAlias).containsExactly("kestrel#euw");
    }

    @Test
    void replacingAliasesKeepsTheOnesThatStay() {
        player.replaceAliases(List.of(new NewAlias("Kes", AliasKind.NICKNAME), new NewAlias("K-old", AliasKind.OTHER)));
        PlayerAlias kept = player.getAliases().getFirst();

        player.replaceAliases(
                List.of(new NewAlias("Kes", AliasKind.NICKNAME), new NewAlias("Birdie", AliasKind.NICKNAME)));

        assertThat(player.getAliases()).extracting(PlayerAlias::getAlias).containsExactly("Kes", "Birdie");
        assertThat(player.getAliases().getFirst()).isSameAs(kept);
    }
}
