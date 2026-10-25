package dev.fascodes.ticketSystem.event.entity;

import dev.fascodes.ticketSystem.user.entity.User;
import dev.fascodes.ticketSystem.venue.entity.Venue;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
public class Event {
    @Id
    @GeneratedValue
    private UUID id;

    // TODO: Here we probably do not need entire owner entity even if it is LAZY fetch - token holds UUID inside to compare
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Instant startDate;

    @Column(nullable = false)
    private Instant endDate;

    @Column(nullable = false)
    private Instant ticketSaleDate;

    @Column(nullable = false)
    private Boolean published = false;

    public boolean isPublished() {
        return this.published;
    }
}
