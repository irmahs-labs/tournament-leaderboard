package dev.irmahs.leaderboard.players;

import dev.irmahs.leaderboard.ranks.Rank;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

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

    @OneToMany(mappedBy = "player", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 100)
    private List<PlayerAlias> aliases = new ArrayList<>();

    protected Player() {}

    public Player(UUID organizationId, String riotId, String discordUsername, Rank currentRank) {
        this.organizationId = organizationId;
        this.riotId = riotId;
        this.discordUsername = discordUsername;
        this.currentRank = currentRank;
    }

    /**
     * Gives the player a new Riot ID. The old one becomes a {@link AliasKind#PREVIOUS_RIOT_ID}
     * alias, so search still finds them by it. Changing only the letter case adds no alias, and
     * going back to a previous Riot ID drops it from the aliases.
     */
    public void rename(String newRiotId) {
        if (riotId.equals(newRiotId)) {
            return;
        }
        if (!riotId.equalsIgnoreCase(newRiotId) && !hasAlias(riotId)) {
            aliases.add(new PlayerAlias(this, riotId, AliasKind.PREVIOUS_RIOT_ID));
        }
        aliases.removeIf(alias -> alias.getKind() == AliasKind.PREVIOUS_RIOT_ID
                && alias.getAlias().equalsIgnoreCase(newRiotId));
        riotId = newRiotId;
    }

    public void setDiscordUsername(String discordUsername) {
        this.discordUsername = discordUsername;
    }

    public void setCurrentRank(Rank currentRank) {
        this.currentRank = currentRank;
    }

    /** Replaces the player's aliases, keeping the ones that stay as they were. */
    public void replaceAliases(List<NewAlias> wanted) {
        aliases.removeIf(alias -> wanted.stream()
                .noneMatch(next -> next.alias().equals(alias.getAlias()) && next.kind() == alias.getKind()));
        for (NewAlias next : wanted) {
            boolean kept = aliases.stream()
                    .anyMatch(alias -> next.alias().equals(alias.getAlias()) && next.kind() == alias.getKind());
            if (!kept) {
                aliases.add(new PlayerAlias(this, next.alias(), next.kind()));
            }
        }
    }

    private boolean hasAlias(String name) {
        return aliases.stream().anyMatch(alias -> alias.getAlias().equalsIgnoreCase(name));
    }

    /** An alias as an admin types it. */
    public record NewAlias(String alias, AliasKind kind) {}

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

    public List<PlayerAlias> getAliases() {
        return Collections.unmodifiableList(aliases);
    }
}
