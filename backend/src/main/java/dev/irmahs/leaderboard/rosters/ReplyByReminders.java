package dev.irmahs.leaderboard.rosters;

import dev.irmahs.leaderboard.matches.Match;
import dev.irmahs.leaderboard.matches.MatchRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two days before a match's reply-by date, reminds the players signed up who haven't answered.
 * Each match is reminded once: with two versions of the app running during a deploy, only one
 * marks it.
 */
@Component
class ReplyByReminders {

    static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    static final int DAYS_BEFORE = 2;

    private final MatchRepository matches;
    private final RosterService rosters;
    private final ApplicationEventPublisher events;

    ReplyByReminders(MatchRepository matches, RosterService rosters, ApplicationEventPublisher events) {
        this.matches = matches;
        this.rosters = rosters;
        this.events = events;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "Europe/Paris")
    @Transactional
    public void remind() {
        LocalDate today = LocalDate.now(ZONE);
        for (Match match : matches.findDueForReminder(today, today.plusDays(DAYS_BEFORE))) {
            if (matches.markReminded(match.getId(), Instant.now()) == 1) {
                events.publishEvent(new Reminded(match.getOrganizationId(), match.getId(), rosters.notAnswered(match)));
            }
        }
    }
}
