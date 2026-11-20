package dev.irmahs.leaderboard.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OnlineCountTest {

    private final OnlineCount online = new OnlineCount();

    @Test
    void countsEachOpenPage() {
        online.join(1_000);
        online.join(1_000);

        assertThat(online.count()).isEqualTo(2);
    }

    @Test
    void sendsTheCountWithoutFailingOnStreamsNotYetOpen() {
        online.join(1_000);

        assertThatCode(online::sendToAll).doesNotThrowAnyException();
        assertThat(online.count()).isEqualTo(1);
    }

    @Test
    void sendsChangesOnlyWhenTheCountChanged() {
        online.join(1_000);

        assertThatCode(online::sendChanges).doesNotThrowAnyException();
        assertThatCode(online::sendChanges).doesNotThrowAnyException();
        assertThat(online.count()).isEqualTo(1);
    }

    @Test
    void endsEveryStreamWhenTheAppStops() {
        online.join(1_000);
        online.join(1_000);

        online.closeAll();

        assertThat(online.count()).isZero();
    }

    @Test
    void turnsAwayPagesAboveTheLimit() {
        for (int i = 0; i < OnlineCount.MAX_STREAMS; i++) {
            online.join(1_000);
        }

        assertThatThrownBy(() -> online.join(1_000))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(online.count()).isEqualTo(OnlineCount.MAX_STREAMS);
    }
}
