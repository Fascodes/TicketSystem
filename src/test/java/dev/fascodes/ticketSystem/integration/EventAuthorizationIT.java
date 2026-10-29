package dev.fascodes.ticketSystem.integration;

import dev.fascodes.ticketSystem.config.PostgresTestConfig;
import dev.fascodes.ticketSystem.event.entity.Event;
import dev.fascodes.ticketSystem.event.repository.EventRepository;
import dev.fascodes.ticketSystem.security.filter.AuthenticatedUser;
import dev.fascodes.ticketSystem.user.entity.User;
import dev.fascodes.ticketSystem.user.repository.UserRepository;
import dev.fascodes.ticketSystem.venue.entity.Venue;
import dev.fascodes.ticketSystem.venue.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class EventAuthorizationIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    EventRepository events;
    @Autowired
    VenueRepository venues;

    private static RequestPostProcessor asUser(UUID id, String... roles) {
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(id, "x@test.local"), null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private User persistUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        return users.save(u);
    }

    // TODO: Consider switching from Instant to Clock - Bean in tests .fixed() in prod
    private Event persistEvent(User owner, boolean published) {
        Venue venue = new Venue();
        venue.setName("Test Venue");
        venues.save(venue);
        Event e = new Event();
        e.setOwner(owner);
        e.setVenue(venue);
        e.setName("Test");
        e.setStartDate(Instant.now().plusSeconds(3600));
        e.setEndDate(Instant.now().plusSeconds(7200));
        e.setTicketSaleDate(Instant.now());
        e.setPublished(published);
        return events.save(e);
    }

    private static RequestPostProcessor actor(String actor, User owner) {
        return switch (actor) {
            case "OWNER" -> asUser(owner.getId(), "ROLE_USER");
            case "OTHER" -> asUser(UUID.randomUUID(), "ROLE_USER");
            case "ADMIN" -> asUser(UUID.randomUUID(), "ROLE_ADMIN");
            default -> SecurityMockMvcRequestPostProcessors.anonymous();
        };
    }

    // ---------- organizer: owner or admin ----------

    @ParameterizedTest(name = "organizer: {0}, published={1} -> {2}")
    @CsvSource({
            "OWNER, true,  200", "OWNER, false, 200",
            "OTHER, true,  403", "OTHER, false, 403",
            "ADMIN, true,  200", "ADMIN, false, 200",
            "ANON,  true,  401", "ANON,  false, 401"
    })
    void organizerGetEvent(String actor, boolean published, int expected) throws Exception {
        User owner = persistUser();
        UUID eventId = persistEvent(owner, published).getId();

        mvc.perform(get("/api/organizer/events/{id}", eventId).with(actor(actor, owner)))
                .andExpect(status().is(expected));
    }

    // TODO: Similar tests for update/delete

    @Test
    void organizerNonexistentEventIsForbiddenForUserAndNotFoundForAdmin() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/api/organizer/events/{id}", missing).with(asUser(UUID.randomUUID(), "ROLE_USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/organizer/events/{id}", missing).with(asUser(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ---------- public: published only ----------

    @ParameterizedTest(name = "public: {0}, published={1} -> {2}")
    @CsvSource({
            "OWNER, true,  200", "OWNER, false, 404",
            "OTHER, true,  200", "OTHER, false, 404",
            "ADMIN, true,  200", "ADMIN, false, 404",
            "ANON,  true,  200", "ANON,  false, 404"
    })
    void publicGetEvent(String actor, boolean published, int expected) throws Exception {
        User owner = persistUser();
        UUID eventId = persistEvent(owner, published).getId();

        mvc.perform(get("/api/public/events/{id}", eventId).with(actor(actor, owner)))
                .andExpect(status().is(expected));
    }

    @Test
    void publicResponseDoesNotExposeInternalFields() throws Exception {
        UUID eventId = persistEvent(persistUser(), true).getId();

        mvc.perform(get("/api/public/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.published").doesNotExist());
    }

    @Test
    void publicNonexistentEventIsNotFound() throws Exception {
        mvc.perform(get("/api/public/events/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
