package dev.fascodes.ticketSystem.auth.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name="users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Boolean enabled = false;

    @Column(nullable = false)
    private Boolean accountNonLocked = true;

    private Instant lockedUntil;

    private Integer failedLoginAttempts;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant lastLoginAt;

    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    void onCreate(){
        this.createdAt = Instant.now();
        this.lastLoginAt = Instant.now();
        this.failedLoginAttempts = 0; // TODO: Reevaluate at laster stages of auth
    }

    public boolean isAccountNonLocked() {
        return this.accountNonLocked;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

}
