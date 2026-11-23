package dev.irmahs.leaderboard.organizations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An organisation's logo: a square PNG, JPEG, WebP or SVG, shown in its header. */
@Entity
@Table(name = "organization_logos")
class OrganizationLogo {

    @Id
    private UUID organizationId;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private byte[] bytes;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OrganizationLogo() {}

    OrganizationLogo(UUID organizationId) {
        this.organizationId = organizationId;
    }

    void replace(String contentType, byte[] bytes, Instant when) {
        this.contentType = contentType;
        this.bytes = bytes.clone();
        this.updatedAt = when;
    }

    String getContentType() {
        return contentType;
    }

    byte[] getBytes() {
        return bytes.clone();
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
