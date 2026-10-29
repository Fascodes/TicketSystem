package dev.fascodes.ticketSystem.security;

import dev.fascodes.ticketSystem.security.ratelimit.RateLimitFilter;
import dev.fascodes.ticketSystem.security.ratelimit.RateLimitRule;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(
            Map.of("/api/auth/login", new RateLimitRule(3, Duration.ofHours(1))), new ObjectMapper());

    private MockHttpServletResponse post(String path, String ip) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void rejectsAfterCapacityWithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        var blocked = post("/api/auth/login", "1.1.1.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isPositive();
    }

    @Test
    void limitsAreIndependentPerIp() throws Exception {
        for (int i = 0; i < 3; i++) post("/api/auth/login", "1.1.1.1");
        assertThat(post("/api/auth/login", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void unlistedPathsAreNotLimited() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(post("/api/other", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }
}