package dev.fascodes.ticketSystem.integration;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Each test creates its own venue and filters by venueId - the database is shared between tests
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, TestClockConfig.class, TestJwtKeysConfig.class})
class EventSearchIT {

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

    private User persistUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        return users.save(u);
    }

    private Venue persistVenue() {
        Venue venue = new Venue();
        venue.setName("Test Venue");
        return venues.save(venue);
    }

    private Event persistEvent(User owner, Venue venue, String name, EventCategory category,
                               long startOffsetSeconds, boolean published) {
        Instant start = clock.instant().plusSeconds(3600 + startOffsetSeconds);
        Event e = new Event();
        e.setOwner(owner);
        e.setVenue(venue);
        e.setName(name);
        e.setCategory(category);
        e.setStartDate(start);
        e.setEndDate(start.plusSeconds(3600));
        e.setTicketSaleDate(clock.instant());
        e.setPublished(published);
        return events.save(e);
    }

    // ---------- public list ----------

    @Test
    void publicListReturnsOnlyPublishedEventsAndCannotBeOverridden() throws Exception {
        User owner = persistUser();
        Venue venue = persistVenue();
        persistEvent(owner, venue, "Shown", EventCategory.JAZZ, 0, true);
        persistEvent(owner, venue, "Hidden", EventCategory.JAZZ, 0, false);

        mvc.perform(get("/api/public/events")
                        .param("venueId", venue.getId().toString())
                        .param("published", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].name", contains("Shown")))
                .andExpect(jsonPath("$.content[0].ownerId").doesNotExist())
                .andExpect(jsonPath("$.content[0].published").doesNotExist())
                .andExpect(jsonPath("$.content[0].category").value("JAZZ"));
    }

    @Test
    void publicListFiltersByCategoryAndName() throws Exception {
        User owner = persistUser();
        Venue venue = persistVenue();
        persistEvent(owner, venue, "Jazz Night", EventCategory.JAZZ, 0, true);
        persistEvent(owner, venue, "Rock Night", EventCategory.ROCK, 0, true);
        persistEvent(owner, venue, "Pop Morning", EventCategory.POP, 0, true);

        mvc.perform(get("/api/public/events")
                        .param("venueId", venue.getId().toString())
                        .param("category", "JAZZ", "ROCK")
                        .param("name", "night"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Jazz Night", "Rock Night")));
    }

    @Test
    void defaultSortIsStartDateWithStableTieBreaker() throws Exception {
        User owner = persistUser();
        Venue venue = persistVenue();
        persistEvent(owner, venue, "Later", EventCategory.POP, 100, true);
        // Same start date - the id tie-breaker must split them deterministically across pages
        persistEvent(owner, venue, "SameA", EventCategory.POP, 0, true);
        persistEvent(owner, venue, "SameB", EventCategory.POP, 0, true);

        mvc.perform(get("/api/public/events").param("venueId", venue.getId().toString()))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("SameA", "SameB", "Later")))
                .andExpect(jsonPath("$.content[2].name").value("Later"));

        mvc.perform(get("/api/public/events")
                        .param("venueId", venue.getId().toString()).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].name", contains("Later")));
    }

    @Test
    void sortByNameDescending() throws Exception {
        User owner = persistUser();
        Venue venue = persistVenue();
        persistEvent(owner, venue, "A", EventCategory.POP, 0, true);
        persistEvent(owner, venue, "B", EventCategory.POP, 0, true);

        mvc.perform(get("/api/public/events")
                        .param("venueId", venue.getId().toString()).param("sort", "name,desc"))
                .andExpect(jsonPath("$.content[*].name", contains("B", "A")));
    }

    @Test
    void sortingByNonWhitelistedPropertyIsBadRequest() throws Exception {
        mvc.perform(get("/api/public/events").param("sort", "owner.passwordHash"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/events").param("sort", "published"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageSizeIsCappedAt100() throws Exception {
        mvc.perform(get("/api/public/events").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void invalidFilterValuesAreBadRequest() throws Exception {
        Instant now = clock.instant();
        mvc.perform(get("/api/public/events")
                        .param("from", now.plusSeconds(10).toString())
                        .param("to", now.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/events").param("category", "NOT_A_CATEGORY"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/organizer/events").param("published", "maybe")
                        .with(asUser(UUID.randomUUID(), "ROLE_USER")))
                .andExpect(status().isBadRequest());
    }

    // ---------- organizer list ----------

    @Test
    void organizerSeesOnlyOwnEventsIncludingDrafts() throws Exception {
        User owner = persistUser();
        User other = persistUser();
        Venue venue = persistVenue();
        persistEvent(owner, venue, "Mine published", EventCategory.ROCK, 0, true);
        persistEvent(owner, venue, "Mine draft", EventCategory.ROCK, 10, false);
        persistEvent(other, venue, "Not mine", EventCategory.ROCK, 20, true);

        mvc.perform(get("/api/organizer/events")
                        .param("venueId", venue.getId().toString())
                        .with(asUser(owner.getId(), "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", contains("Mine published", "Mine draft")));

        mvc.perform(get("/api/organizer/events")
                        .param("venueId", venue.getId().toString())
                        .param("published", "false")
                        .with(asUser(owner.getId(), "ROLE_USER")))
                .andExpect(jsonPath("$.content[*].name", contains("Mine draft")));
    }

    @Test
    void adminSeesAllEvents() throws Exception {
        Venue venue = persistVenue();
        persistEvent(persistUser(), venue, "One", EventCategory.ROCK, 0, false);
        persistEvent(persistUser(), venue, "Two", EventCategory.ROCK, 10, true);

        mvc.perform(get("/api/organizer/events")
                        .param("venueId", venue.getId().toString())
                        .with(asUser(UUID.randomUUID(), "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].name", contains("One", "Two")));
    }

    @Test
    void organizerListRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/organizer/events")).andExpect(status().isUnauthorized());
    }

    // ---------- category on create ----------

    @Test
    void createRequiresCategory() throws Exception {
        User owner = persistUser();
        Venue venue = persistVenue();
        Instant start = clock.instant().plusSeconds(3600);
        String body = """
                {"name":"Gig","venueId":"%s","startDate":"%s","endDate":"%s","ticketSaleDate":"%s","published":true}
                """.formatted(venue.getId(), start, start.plusSeconds(3600), clock.instant());

        mvc.perform(post("/api/organizer/events").with(asUser(owner.getId(), "ROLE_USER"))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/organizer/events").with(asUser(owner.getId(), "ROLE_USER"))
                        .contentType(APPLICATION_JSON)
                        .content(body.replace("\"published\"", "\"category\":\"METAL\",\"published\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("METAL"));
    }
}
