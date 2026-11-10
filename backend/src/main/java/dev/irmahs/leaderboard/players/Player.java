package dev.irmahs.leaderboard.players;

import dev.irmahs.leaderboard.ranks.Rank;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A player of one organisation. The same person playing at two organisations is two players;
 * the Riot ID is unique within an organisation, whatever its letter case.
 */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID organizationId;

    /** The current Riot ID, shown everywhere: Kestrel#EUW. */
    @Column(nullable = false)
    private String riotId;

    private String discordUsername;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_rank_id")
    private Rank currentRank;

    protected Player() {}

    public Player(UUID organizationId, String riotId, String discordUsername, Rank currentRank) {
        this.organizationId = organizationId;
        this.riotId = riotId;
        this.discordUsername = discordUsername;
        this.currentRank = currentRank;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getRiotId() {
        return riotId;
    }

    public String getDiscordUsername() {
        return discordUsername;
    }

    public Rank getCurrentRank() {
        return currentRank;
    }
}
