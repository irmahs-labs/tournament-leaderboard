package dev.irmahs.leaderboard.notices;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** The notifications table, through plain SQL: the details are JSON. */
@Repository
class NoticeStore {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    NoticeStore(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void add(
            UUID userId,
            UUID organizationId,
            NoticeKind kind,
            String subject,
            Map<String, Object> details,
            Instant shownFrom) {
        jdbc.sql("""
                        INSERT INTO notifications (user_id, organization_id, kind, subject, payload, shown_from)
                        VALUES (:user, :org, :kind, :subject, CAST(:payload AS jsonb), :shownFrom)""")
                .param("user", userId)
                .param("org", organizationId)
                .param("kind", kind.name())
                .param("subject", subject)
                .param("payload", json.writeValueAsString(details))
                .param("shownFrom", java.sql.Timestamp.from(shownFrom))
                .update();
    }

    /** The kind of an unread notice about this subject that isn't shown yet. */
    Optional<NoticeKind> pending(UUID userId, String subject, Instant now) {
        return jdbc.sql("""
                        SELECT kind FROM notifications
                        WHERE user_id = :user AND subject = :subject AND read_at IS NULL AND shown_from > :now""")
                .param("user", userId)
                .param("subject", subject)
                .param("now", java.sql.Timestamp.from(now))
                .query(String.class)
                .optional()
                .map(NoticeKind::valueOf);
    }

    /** Replaces an unread notice about this subject, shown or not; whether there was one. */
    boolean replaceUnread(
            UUID userId, String subject, NoticeKind kind, Map<String, Object> details, Instant shownFrom) {
        return jdbc.sql("""
                        UPDATE notifications SET kind = :kind, payload = CAST(:payload AS jsonb), shown_from = :shownFrom
                        WHERE user_id = :user AND subject = :subject AND read_at IS NULL""")
                        .param("user", userId)
                        .param("subject", subject)
                        .param("kind", kind.name())
                        .param("payload", json.writeValueAsString(details))
                        .param("shownFrom", java.sql.Timestamp.from(shownFrom))
                        .update()
                > 0;
    }

    List<Notice> shown(UUID userId, Instant now, int limit) {
        return jdbc.sql("""
                        SELECT n.id, n.kind, n.payload::text AS payload, n.shown_from, n.read_at IS NOT NULL AS read,
                               o.slug, o.name
                        FROM notifications n JOIN organizations o ON o.id = n.organization_id
                        WHERE n.user_id = :user AND n.shown_from <= :now
                        ORDER BY n.shown_from DESC, n.id DESC
                        LIMIT :limit""")
                .param("user", userId)
                .param("now", java.sql.Timestamp.from(now))
                .param("limit", limit)
                .query((row, index) -> new Notice(
                        row.getLong("id"),
                        NoticeKind.valueOf(row.getString("kind")),
                        row.getString("slug"),
                        row.getString("name"),
                        json.readValue(row.getString("payload"), Map.class),
                        row.getTimestamp("shown_from").toInstant(),
                        row.getBoolean("read")))
                .list();
    }

    int unread(UUID userId, Instant now) {
        return jdbc.sql("""
                        SELECT count(*) FROM notifications
                        WHERE user_id = :user AND read_at IS NULL AND shown_from <= :now""")
                .param("user", userId)
                .param("now", java.sql.Timestamp.from(now))
                .query(Integer.class)
                .single();
    }

    void markRead(UUID userId, Instant now) {
        jdbc.sql("""
                        UPDATE notifications SET read_at = :now
                        WHERE user_id = :user AND read_at IS NULL AND shown_from <= :now""")
                .param("user", userId)
                .param("now", java.sql.Timestamp.from(now))
                .update();
    }

    /** Forgets notices read before this moment. */
    int forgetReadBefore(Instant before) {
        return jdbc.sql("DELETE FROM notifications WHERE read_at < :before")
                .param("before", java.sql.Timestamp.from(before))
                .update();
    }

    /**
     * A notice as its reader sees it.
     *
     * @param details what it's about, by kind: a match's season, number, date and place; who changed
     *     what; a claim's Riot ID and answer
     */
    record Notice(
            long id,
            NoticeKind kind,
            String organizationSlug,
            String organizationName,
            Map<String, Object> details,
            Instant at,
            boolean read) {}
}
