package dev.fascodes.ticketSystem.repository;

import dev.fascodes.ticketSystem.config.PostgresTestConfig;
import dev.fascodes.ticketSystem.event.entity.Event;
import dev.fascodes.ticketSystem.event.repository.EventRepository;
import dev.fascodes.ticketSystem.user.entity.User;
import dev.fascodes.ticketSystem.venue.entity.Venue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

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

    private User persistUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        return em.persistAndFlush(u);
    }

    private Event persistEvent(User owner, boolean published) {
        Venue venue = new Venue();
        venue.setName("Test Venue");
        em.persist(venue);

        Event e = new Event();
        e.setOwner(owner);
        e.setVenue(venue);
        e.setName("Test");
        e.setStartDate(Instant.now().plusSeconds(3600));
        e.setEndDate(Instant.now().plusSeconds(7200));
        e.setTicketSaleDate(Instant.now());
        e.setPublished(published);
        return em.persistAndFlush(e);
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
}
