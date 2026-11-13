package dev.irmahs.leaderboard.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The organisation the current request is about, if any. Every transaction started while it's
 * set tells the database, whose row-level security then hides other organisations' rows.
 */
public final class OrganizationScope {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private OrganizationScope() {}

    public static Optional<UUID> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Runs the work inside one organisation's scope, then puts the previous scope back. */
    public static <T> T call(UUID organizationId, Supplier<T> work) {
        UUID previous = CURRENT.get();
        CURRENT.set(organizationId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    static void enter(UUID organizationId) {
        CURRENT.set(organizationId);
    }

    static void leave() {
        CURRENT.remove();
    }
}
