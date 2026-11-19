package dev.irmahs.leaderboard.organizations;

import static org.assertj.core.api.Assertions.assertThat;

import dev.irmahs.leaderboard.organizations.BrandingService.Logo;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LogoTest {

    static final Instant WHEN = Instant.parse("2026-11-19T10:00:00Z");

    @Test
    void twoLogosWithTheSameBytesAreEqual() {
        Logo one = new Logo("image/png", new byte[] {1, 2, 3}, WHEN);
        Logo same = new Logo("image/png", new byte[] {1, 2, 3}, WHEN);

        assertThat(one).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(one).isNotEqualTo(new Logo("image/png", new byte[] {1, 2, 4}, WHEN));
        assertThat(one).hasToString("Logo[image/png, 3 bytes, 2026-11-19T10:00:00Z]");
    }

    @Test
    void imagesAreToldApartByTheirFirstBytes() {
        assertThat(BrandingService.imageType("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes()))
                .contains("image/svg+xml");
        assertThat(BrandingService.imageType("<svg><script>alert(1)</script></svg>".getBytes()))
                .isEmpty();
        assertThat(BrandingService.imageType(new byte[] {1, 2})).isEmpty();
    }
}
