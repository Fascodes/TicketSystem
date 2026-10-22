package dev.fascodes.ticketSystem.auth.token;

import dev.fascodes.ticketSystem.auth.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(nullable = false, unique = true)
    String tokenHash;

    @Column(nullable = false)
    Instant expiresAt;

    @Column(nullable = false)
    Boolean revoked = false;

    UUID replacedByTokenId;

    @Column(nullable = false)
    Instant createdAt;

    String userAgent;
    String ipAddress;

    @PrePersist
    void onCreate(){
        this.createdAt = Instant.now();
    }
}
