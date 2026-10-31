package dev.fascodes.ticketSystem.event.repository;

import dev.fascodes.ticketSystem.event.entity.Event;
import dev.fascodes.ticketSystem.event.entity.EventCategory;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

// Request-driven filters return null for an empty value - the caller drops nulls before combining.
// isPublished() and ownedBy() are access constraints: only the service applies them, never request input.
public final class EventSpecifications {

    private EventSpecifications() {}

    public static Specification<Event> isPublished() {
        return (root, query, cb) -> cb.isTrue(root.get("published"));
    }

    public static Specification<Event> ownedBy(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
    }

    public static Specification<Event> published(Boolean published) {
        if (published == null) return null;
        return (root, query, cb) -> cb.equal(root.get("published"), published);
    }

    // venue.id is the FK column on events - no join needed
    public static Specification<Event> hasVenue(UUID venueId) {
        if (venueId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("venue").get("id"), venueId);
    }

    public static Specification<Event> inCategories(Collection<EventCategory> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return (root, query, cb) -> root.get("category").in(categories);
    }

    public static Specification<Event> nameContains(String text) {
        if (text == null || text.isBlank()) return null;
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\');
    }

    public static Specification<Event> startsFrom(Instant from) {
        if (from == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startDate"), from);
    }

    public static Specification<Event> startsTo(Instant to) {
        if (to == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startDate"), to);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
