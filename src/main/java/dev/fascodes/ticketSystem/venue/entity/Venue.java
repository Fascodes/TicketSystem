package dev.fascodes.ticketSystem.venue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "venues")
@Getter
@Setter
public class Venue {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;
}
