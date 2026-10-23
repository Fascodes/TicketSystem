package dev.fascodes.ticketSystem.auth.refresh;

import dev.fascodes.ticketSystem.user.entity.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Getter
    @Id
    @GeneratedValue
    UUID id;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Setter
    @Column(nullable = false, unique = true)
    String tokenHash;

    @Setter
    @Getter
    @Column(nullable = false)
    Instant expiresAt;

    @Setter
    @Column(nullable = false)
    Boolean revoked = false;

    @Setter
    UUID replacedByTokenId;

    @Column(nullable = false)
    Instant createdAt;

    @Setter
    String userAgent;

    @Setter
    String ipAddress;

    @PrePersist
    void onCreate(){
        this.createdAt = Instant.now();
    }

    public boolean isRevoked() {
        return this.revoked;
    }
}
