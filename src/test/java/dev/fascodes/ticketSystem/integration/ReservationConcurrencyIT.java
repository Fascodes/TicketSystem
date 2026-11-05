package dev.fascodes.ticketSystem.integration;

import dev.fascodes.ticketSystem.common.MutableClock;
import dev.fascodes.ticketSystem.config.PostgresTestConfig;
import dev.fascodes.ticketSystem.config.TestClockConfig;
import dev.fascodes.ticketSystem.config.TestJwtKeysConfig;
import dev.fascodes.ticketSystem.event.entity.Event;
import dev.fascodes.ticketSystem.event.entity.EventCategory;
import dev.fascodes.ticketSystem.event.repository.EventRepository;
import dev.fascodes.ticketSystem.reservation.dto.ReservationRequest;
import dev.fascodes.ticketSystem.reservation.entity.ReservationStatus;
import dev.fascodes.ticketSystem.reservation.service.ReservationService;
import dev.fascodes.ticketSystem.security.filter.AuthenticatedUser;
import dev.fascodes.ticketSystem.user.entity.User;
import dev.fascodes.ticketSystem.user.repository.UserRepository;
import dev.fascodes.ticketSystem.venue.entity.Venue;
import dev.fascodes.ticketSystem.venue.repository.VenueRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// No @Transactional on the test: every pay() runs in its own real transaction and competes for the row lock
@SpringBootTest
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, TestClockConfig.class, TestJwtKeysConfig.class})
class ReservationConcurrencyIT {

    private static final int CAPACITY = 5;
    private static final int BUYERS = 20;

    @Autowired
    ReservationService reservations;
    @Autowired
    UserRepository users;
    @Autowired
    EventRepository events;
    @Autowired
    VenueRepository venues;
    @Autowired
    MutableClock clock;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, "x@test.local"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private User persistUser() {
        User u = new User();
        u.setEmail(UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        return users.save(u);
    }

    private Event persistOpenEvent() {
        Instant now = clock.instant();
        Venue venue = new Venue();
        venue.setName("Arena");
        venues.save(venue);
        Event e = new Event();
        e.setOwner(persistUser());
        e.setVenue(venue);
        e.setName("Sold out show");
        e.setCategory(EventCategory.POP);
        e.setStartDate(now.plus(Duration.ofDays(1)));
        e.setEndDate(now.plus(Duration.ofDays(1)).plusSeconds(3600));
        e.setTicketSaleDate(now.minusSeconds(60));
        e.setCapacity(CAPACITY);
        e.setAvailableSeats(CAPACITY);
        e.setPublished(true);
        return events.save(e);
    }

    @Test
    void concurrentPaymentsNeverOversellThePool() throws Exception {
        Event event = persistOpenEvent();

        // Every buyer holds a PENDING reservation - none of them took a seat yet
        record Buyer(UUID userId, UUID reservationId) {}
        List<Buyer> buyers = new ArrayList<>();
        for (int i = 0; i < BUYERS; i++) {
            User user = persistUser();
            authenticate(user.getId());
            UUID reservationId = reservations.create(new ReservationRequest(event.getId(), 1), user.getId()).id();
            buyers.add(new Buyer(user.getId(), reservationId));
        }
        SecurityContextHolder.clearContext();

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        List<Future<ReservationStatus>> results = new ArrayList<>();
        for (Buyer buyer : buyers) {
            Callable<ReservationStatus> payment = () -> {
                authenticate(buyer.userId());
                try {
                    start.await();
                    return reservations.pay(buyer.reservationId()).status();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            results.add(pool.submit(payment));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<ReservationStatus> statuses = new ArrayList<>();
        for (Future<ReservationStatus> result : results) {
            statuses.add(result.get());
        }
        assertThat(statuses).filteredOn(s -> s == ReservationStatus.PAID).hasSize(CAPACITY);
        assertThat(statuses).filteredOn(s -> s == ReservationStatus.FAILED).hasSize(BUYERS - CAPACITY);
        assertThat(events.findById(event.getId()).orElseThrow().getAvailableSeats()).isZero();
    }
}
