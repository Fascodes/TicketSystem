package dev.fascodes.ticketSystem.venue.repository;

import dev.fascodes.ticketSystem.venue.entity.Venue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
}
