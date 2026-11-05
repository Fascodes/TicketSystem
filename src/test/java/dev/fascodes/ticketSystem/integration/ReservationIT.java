package dev.fascodes.ticketSystem.integration;

import com.jayway.jsonpath.JsonPath;
import dev.fascodes.ticketSystem.common.MutableClock;
import dev.fascodes.ticketSystem.config.PostgresTestConfig;
import dev.fascodes.ticketSystem.config.TestClockConfig;
import dev.fascodes.ticketSystem.config.TestJwtKeysConfig;
import dev.fascodes.ticketSystem.event.entity.Event;
import dev.fascodes.ticketSystem.event.entity.EventCategory;
import dev.fascodes.ticketSystem.event.repository.EventRepository;
import dev.fascodes.ticketSystem.security.filter.AuthenticatedUser;
import dev.fascodes.ticketSystem.user.entity.User;
import dev.fascodes.ticketSystem.user.repository.UserRepository;
import dev.fascodes.ticketSystem.venue.entity.Venue;
import dev.fascodes.ticketSystem.venue.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Time-dependent cases move the event dates, not the shared clock
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, TestClockConfig.class, TestJwtKeysConfig.class})
class ReservationIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    EventRepository events;
    @Autowired
    VenueRepository venues;
    @Autowired
    MutableClock clock;

    private static RequestPostProcessor asUser(UUID id, String... roles) {
        var auth = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(id, "x@test.local"), null,
                Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

    private static RequestPostProcessor as(User user) {
        return asUser(user.getId(), "ROLE_USER");
    }

    private User persistUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        return users.save(u);
    }

    private Event persistEvent(int capacity, boolean published, Instant saleDate, Instant start) {
        Venue venue = new Venue();
        venue.setName("Test Venue");
        venues.save(venue);
        Event e = new Event();
        e.setOwner(persistUser());
        e.setVenue(venue);
        e.setName("Concert");
        e.setCategory(EventCategory.ROCK);
        e.setStartDate(start);
        e.setEndDate(start.plusSeconds(3600));
        e.setTicketSaleDate(saleDate);
        e.setCapacity(capacity);
        e.setAvailableSeats(capacity);
        e.setPublished(published);
        return events.save(e);
    }

    // Published, sale already open, starts in a day
    private Event openEvent(int capacity) {
        Instant now = clock.instant();
        return persistEvent(capacity, true, now.minusSeconds(60), now.plus(Duration.ofDays(1)));
    }

    private ResultActions reserve(User user, UUID eventId, int quantity) throws Exception {
        return mvc.perform(post("/api/reservations").with(as(user))
                .contentType(APPLICATION_JSON)
                .content("{\"eventId\":\"%s\",\"quantity\":%d}".formatted(eventId, quantity)));
    }

    private String reserveOk(User user, UUID eventId, int quantity) throws Exception {
        String json = reserve(user, eventId, quantity)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private int availableSeats(UUID eventId) {
        return events.findById(eventId).orElseThrow().getAvailableSeats();
    }

    // ---------- create ----------

    @Test
    void pendingReservationDoesNotTakeSeats() throws Exception {
        Event event = openEvent(10);

        reserveOk(persistUser(), event.getId(), 3);

        assertThat(availableSeats(event.getId())).isEqualTo(10);
    }

    @Test
    void cannotReserveUnpublishedEvent() throws Exception {
        Instant now = clock.instant();
        Event event = persistEvent(10, false, now.minusSeconds(60), now.plus(Duration.ofDays(1)));

        reserve(persistUser(), event.getId(), 1).andExpect(status().isNotFound());
    }

    @Test
    void cannotReserveBeforeSaleOrAfterStart() throws Exception {
        Instant now = clock.instant();
        Event notOnSale = persistEvent(10, true, now.plusSeconds(60), now.plus(Duration.ofDays(1)));
        Event started = persistEvent(10, true, now.minus(Duration.ofDays(2)), now.minusSeconds(60));

        reserve(persistUser(), notOnSale.getId(), 1).andExpect(status().isConflict());
        reserve(persistUser(), started.getId(), 1).andExpect(status().isConflict());
    }

    @Test
    void cannotReserveMoreThanAvailable() throws Exception {
        Event event = openEvent(2);

        reserve(persistUser(), event.getId(), 3).andExpect(status().isConflict());
    }

    @Test
    void onlyOnePendingReservationPerUserAndEvent() throws Exception {
        Event event = openEvent(10);
        User user = persistUser();

        String first = reserveOk(user, event.getId(), 1);
        reserve(user, event.getId(), 1).andExpect(status().isConflict());

        // Once the first one is closed, a new one is allowed
        mvc.perform(post("/api/reservations/{id}/cancel", first).with(as(user))).andExpect(status().isOk());
        reserveOk(user, event.getId(), 1);
    }

    @Test
    void quantityOutsideRangeIsBadRequest() throws Exception {
        Event event = openEvent(10);

        reserve(persistUser(), event.getId(), 0).andExpect(status().isBadRequest());
        reserve(persistUser(), event.getId(), 7).andExpect(status().isBadRequest());
    }

    // ---------- pay ----------

    @Test
    void payTakesSeatsAndIsIdempotent() throws Exception {
        Event event = openEvent(10);
        User user = persistUser();
        String id = reserveOk(user, event.getId(), 4);

        mvc.perform(post("/api/reservations/{id}/pay", id).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        mvc.perform(post("/api/reservations/{id}/pay", id).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(availableSeats(event.getId())).isEqualTo(6);
    }

    @Test
    void payWithoutEnoughSeatsFailsAndKeepsPool() throws Exception {
        Event event = openEvent(3);
        User first = persistUser();
        User second = persistUser();
        String a = reserveOk(first, event.getId(), 2);
        String b = reserveOk(second, event.getId(), 2);

        mvc.perform(post("/api/reservations/{id}/pay", a).with(as(first)))
                .andExpect(jsonPath("$.status").value("PAID"));
        // Status FAILED must be committed, so this is a 200 with the outcome, not an error
        mvc.perform(post("/api/reservations/{id}/pay", b).with(as(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        assertThat(availableSeats(event.getId())).isEqualTo(1);
        mvc.perform(get("/api/reservations/{id}", b).with(as(second)))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void cannotPayCancelledReservation() throws Exception {
        Event event = openEvent(10);
        User user = persistUser();
        String id = reserveOk(user, event.getId(), 1);
        mvc.perform(post("/api/reservations/{id}/cancel", id).with(as(user))).andExpect(status().isOk());

        mvc.perform(post("/api/reservations/{id}/pay", id).with(as(user))).andExpect(status().isConflict());
        assertThat(availableSeats(event.getId())).isEqualTo(10);
    }

    // ---------- cancel ----------

    @Test
    void cancelPendingKeepsPoolAndPaidCannotBeCancelled() throws Exception {
        Event event = openEvent(10);
        User user = persistUser();
        String pending = reserveOk(user, event.getId(), 2);

        mvc.perform(post("/api/reservations/{id}/cancel", pending).with(as(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        String paid = reserveOk(user, event.getId(), 2);
        mvc.perform(post("/api/reservations/{id}/pay", paid).with(as(user)));
        mvc.perform(post("/api/reservations/{id}/cancel", paid).with(as(user))).andExpect(status().isConflict());

        assertThat(availableSeats(event.getId())).isEqualTo(8);
    }

    // ---------- access ----------

    @Test
    void reservationIsVisibleOnlyToOwnerAndAdmin() throws Exception {
        Event event = openEvent(10);
        User owner = persistUser();
        String id = reserveOk(owner, event.getId(), 1);

        mvc.perform(get("/api/reservations/{id}", id).with(as(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/reservations/{id}", id).with(as(persistUser()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reservations/{id}", id).with(asUser(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/reservations/{id}/pay", id).with(as(persistUser()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/reservations/{id}/cancel", id).with(as(persistUser()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reservations/{id}", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsOnlyOwnReservationsFilteredByStatus() throws Exception {
        User user = persistUser();
        String paid = reserveOk(user, openEvent(10).getId(), 1);
        mvc.perform(post("/api/reservations/{id}/pay", paid).with(as(user)));
        reserveOk(user, openEvent(10).getId(), 1);
        reserveOk(persistUser(), openEvent(10).getId(), 1);

        mvc.perform(get("/api/reservations").with(as(user)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/reservations").param("status", "PAID").with(as(user)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(paid));
        mvc.perform(get("/api/reservations").param("sort", "user.email").with(as(user)))
                .andExpect(status().isBadRequest());
    }

    // ---------- event side effects ----------

    @Test
    void capacityCannotDropBelowSoldSeats() throws Exception {
        Event event = openEvent(10);
        User buyer = persistUser();
        String id = reserveOk(buyer, event.getId(), 4);
        mvc.perform(post("/api/reservations/{id}/pay", id).with(as(buyer)));

        mvc.perform(put("/api/organizer/events/{id}", event.getId()).with(asUser(event.getOwner().getId(), "ROLE_USER"))
                        .contentType(APPLICATION_JSON).content(eventBody(event, 3)))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/organizer/events/{id}", event.getId()).with(asUser(event.getOwner().getId(), "ROLE_USER"))
                        .contentType(APPLICATION_JSON).content(eventBody(event, 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(20))
                .andExpect(jsonPath("$.availableSeats").value(16));
    }

    @Test
    void eventWithReservationsCannotBeDeleted() throws Exception {
        Event event = openEvent(10);
        reserveOk(persistUser(), event.getId(), 1);

        mvc.perform(delete("/api/organizer/events/{id}", event.getId())
                        .with(asUser(event.getOwner().getId(), "ROLE_USER")))
                .andExpect(status().isConflict());
    }

    private static String eventBody(Event event, int capacity) {
        return """
                {"name":"Concert","venueId":"%s","category":"ROCK","startDate":"%s","endDate":"%s",
                 "ticketSaleDate":"%s","capacity":%d,"published":true}
                """.formatted(event.getVenue().getId(), event.getStartDate(), event.getEndDate(),
                event.getTicketSaleDate(), capacity);
    }
}
