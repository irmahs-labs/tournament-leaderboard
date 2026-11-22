package dev.irmahs.leaderboard.players;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Riot IDs as Riot makes them: a name of 3 to 16 characters, then # and a tag of 3 to 5 letters or
 * numbers, such as Kestrel#EUW. Required whenever a Riot ID is typed; players saved before, many
 * imported without a tag, keep theirs until it's next changed.
 */
public final class RiotIds {

    static final Pattern SHAPE = Pattern.compile("[^#]{3,16}#[\\p{L}\\p{N}]{3,5}");

    private RiotIds() {}

    public static boolean isRiotId(String value) {
        return value != null && SHAPE.matcher(value.strip()).matches();
    }

    /**
     * The Riot ID without its surrounding spaces.
     *
     * @throws ResponseStatusException 400 when it isn't Name#TAG
     */
    public static String require(String value) {
        if (!isRiotId(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A Riot ID is a name of 3 to 16 characters, # and a tag of 3 to 5 letters or numbers,"
                            + " such as Kestrel#EUW");
        }
        return value.strip();
    }
}
