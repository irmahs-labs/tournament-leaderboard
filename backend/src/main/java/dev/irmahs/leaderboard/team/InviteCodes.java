package dev.irmahs.leaderboard.team;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Invite codes: 8 characters from an alphabet without look-alikes (no 0/O, 1/I/L), shown as
 * K7QM-4FXP. That's about 850 billion codes. Only their SHA-256 hash is stored.
 */
final class InviteCodes {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodes() {}

    /** A new code, as it's shown: K7QM-4FXP. */
    static String generate() {
        StringBuilder code = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * The code as typed, without dashes or spaces and in capitals; nothing when it can't be a code,
     * so a lookup isn't even tried.
     */
    static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String code = typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != LENGTH) {
            return null;
        }
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) {
                return null;
            }
        }
        return code;
    }

    /** The hash stored for a code, from its normalized form. */
    static byte[] hash(String normalized) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java has SHA-256", e);
        }
    }
}
