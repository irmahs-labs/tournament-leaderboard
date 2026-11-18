package dev.irmahs.leaderboard.presence;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs {@link OnlineCount}'s heartbeat. */
@Configuration
@EnableScheduling
class PresenceConfig {}
