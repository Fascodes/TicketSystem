package dev.fascodes.ticketSystem.jwt;

import dev.fascodes.ticketSystem.auth.jwt.JwtService;
import dev.fascodes.ticketSystem.common.TestKeys;
import dev.fascodes.ticketSystem.security.filter.AuthenticatedUser;
import dev.fascodes.ticketSystem.security.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private final JwtService jwt = TestKeys.jwtService(Duration.ofMinutes(10));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain run(String authorization) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/me");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        var chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void validTokenSetsAuthenticationWithRoles() throws Exception {
        UUID id = UUID.randomUUID();
        String t = jwt.generateAccessToken(id, "jan@example.com", List.of("ROLE_USER"));

        MockFilterChain chain = run("Bearer " + t);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo(new AuthenticatedUser(id, "jan@example.com"));
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
        assertThat(chain.getRequest()).isNotNull(); // łańcuch kontynuowany
    }

    @Test
    void invalidTokenLeavesContextEmptyButContinuesChain() throws Exception {
        MockFilterChain chain = run("Bearer to.nie.jwt");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void missingOrNonBearerHeaderIsIgnored() throws Exception {
        run(null);
        run("Basic dXNlcjpwYXNz");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
