package dev.fascodes.ticketSystem.repository;

import dev.fascodes.ticketSystem.config.PostgresTestConfig;
import dev.fascodes.ticketSystem.event.entity.Event;
import dev.fascodes.ticketSystem.event.entity.EventCategory;
import dev.fascodes.ticketSystem.event.repository.EventRepository;
import dev.fascodes.ticketSystem.user.entity.User;
import dev.fascodes.ticketSystem.venue.entity.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.fascodes.ticketSystem.event.repository.EventSpecifications.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class EventRepositoryTest {

    @Autowired
    EventRepository events;
    @Autowired
    TestEntityManager em;

    private static final Instant START = Instant.parse("2030-06-01T18:00:00Z");

    private User persistUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        return em.persistAndFlush(u);
    }

    private Venue persistVenue() {
        Venue venue = new Venue();
        venue.setName("Test Venue");
        return em.persist(venue);
    }

    private Event persistEvent(User owner, boolean published) {
        return persistEvent(owner, persistVenue(), "Test", EventCategory.OTHER, START, published);
    }

    private Event persistEvent(User owner, Venue venue, String name, EventCategory category,
                               Instant start, boolean published) {
        Event e = new Event();
        e.setOwner(owner);
        e.setVenue(venue);
        e.setName(name);
        e.setCategory(category);
        e.setStartDate(start);
        e.setEndDate(start.plusSeconds(3600));
        e.setTicketSaleDate(start.minusSeconds(86400));
        e.setPublished(published);
        return em.persistAndFlush(e);
    }

    // Every spec test scopes by its own venue, so rows from other tests never leak in
    private List<String> names(Specification<Event> spec) {
        return events.findAll(spec, Sort.by("name")).stream().map(Event::getName).toList();
    }

    @Test
    void ownerSeesUnpublishedEvent() {
        User owner = persistUser();
        Event e = persistEvent(owner, false);
        assertThat(events.canView(e.getId(), owner.getId())).isTrue();
    }

    @Test
    void strangerDoesNotSeeUnpublishedEvent() {
        Event e = persistEvent(persistUser(), false);
        assertThat(events.canView(e.getId(), persistUser().getId())).isFalse();
    }

    @Test
    void everyoneSeesPublishedEvent() {
        Event e = persistEvent(persistUser(), true);
        assertThat(events.canView(e.getId(), persistUser().getId())).isTrue();
    }

    @Test
    void anonymousSeesOnlyPublished() {
        Event hidden = persistEvent(persistUser(), false);
        Event shown = persistEvent(persistUser(), true);
        assertThat(events.canView(hidden.getId(), null)).isFalse();
        assertThat(events.canView(shown.getId(), null)).isTrue();
    }

    @Test
    void unknownIdIsNeverVisible() {
        assertThat(events.canView(UUID.randomUUID(), persistUser().getId())).isFalse();
    }

    // ---------- specifications ----------

    @Test
    void specificationsFilterByEachCriterion() {
        User owner = persistUser();
        User other = persistUser();
        Venue venue = persistVenue();
        persistEvent(owner, venue, "Jazz Night", EventCategory.JAZZ, START, true);
        persistEvent(owner, venue, "Rock 100%", EventCategory.ROCK, START.plusSeconds(86400), false);
        persistEvent(other, venue, "Classical Evening", EventCategory.CLASSICAL, START.plusSeconds(2 * 86400), true);
        Specification<Event> atVenue = hasVenue(venue.getId());

        assertThat(names(atVenue)).hasSize(3);
        assertThat(names(atVenue.and(isPublished()))).containsExactly("Classical Evening", "Jazz Night");
        assertThat(names(atVenue.and(published(false)))).containsExactly("Rock 100%");
        assertThat(names(atVenue.and(ownedBy(owner.getId())))).containsExactly("Jazz Night", "Rock 100%");
        assertThat(names(atVenue.and(inCategories(Set.of(EventCategory.JAZZ, EventCategory.ROCK)))))
                .containsExactly("Jazz Night", "Rock 100%");
        assertThat(names(atVenue.and(nameContains("  jAzZ ")))).containsExactly("Jazz Night");
        assertThat(names(atVenue.and(startsFrom(START.plusSeconds(86400))).and(startsTo(START.plusSeconds(86400)))))
                .containsExactly("Rock 100%");
    }

    @Test
    void likeWildcardsInNameAreMatchedLiterally() {
        Venue venue = persistVenue();
        User owner = persistUser();
        persistEvent(owner, venue, "Rock 100%", EventCategory.ROCK, START, true);
        persistEvent(owner, venue, "Rock 1000", EventCategory.ROCK, START, true);

        assertThat(names(hasVenue(venue.getId()).and(nameContains("100%")))).containsExactly("Rock 100%");
        assertThat(names(hasVenue(venue.getId()).and(nameContains("_")))).isEmpty();
    }

    @Test
    void emptyFilterValuesProduceNoSpecification() {
        assertThat(published(null)).isNull();
        assertThat(hasVenue(null)).isNull();
        assertThat(inCategories(Set.of())).isNull();
        assertThat(nameContains("  ")).isNull();
        assertThat(startsFrom(null)).isNull();
        assertThat(startsTo(null)).isNull();
    }

    @Test
    void pagedFindAllReturnsTotalsAndLoadsVenue() {
        Venue venue = persistVenue();
        User owner = persistUser();
        for (int i = 0; i < 3; i++) {
            persistEvent(owner, venue, "E" + i, EventCategory.POP, START.plusSeconds(i), true);
        }
        em.clear();

        var page = events.findAll(hasVenue(venue.getId()), PageRequest.of(0, 2, Sort.by("startDate")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Event::getName).containsExactly("E0", "E1");
        assertThat(em.getEntityManager().getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(page.getContent().getFirst().getVenue())).isTrue();
    }
}
