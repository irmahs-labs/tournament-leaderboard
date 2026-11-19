package dev.irmahs.leaderboard.seasons;

import java.math.BigDecimal;

/** How points and point values go out in the API: 422, 85.5 or -1, never 422.00 or 4.22E+2. */
public final class Points {

    private Points() {}

    /** The value without trailing zeros, written plainly; nothing stays nothing. */
    public static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
