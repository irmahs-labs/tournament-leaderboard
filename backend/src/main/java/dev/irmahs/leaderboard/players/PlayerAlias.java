package dev.irmahs.leaderboard.players;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Another name a player is known by. Search finds the player by it too. */
@Entity
@Table(name = "player_aliases")
public class PlayerAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @Column(nullable = false)
    private String alias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AliasKind kind;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PlayerAlias() {}

    PlayerAlias(Player player, String alias, AliasKind kind) {
        this.player = player;
        this.alias = alias;
        this.kind = kind;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAlias() {
        return alias;
    }

    public AliasKind getKind() {
        return kind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
