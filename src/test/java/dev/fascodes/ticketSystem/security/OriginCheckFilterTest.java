package dev.fascodes.ticketSystem.security;

import dev.fascodes.ticketSystem.security.filter.OriginCheckFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OriginCheckFilterTest {

    private final OriginCheckFilter filter = new OriginCheckFilter(Set.of("http://localhost:3000"));

    private MockHttpServletResponse run(String method, String path, String origin, MockFilterChain chain)
            throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        if (origin != null) request.addHeader("Origin", origin);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void allowedOriginPasses() throws Exception {
        var chain = new MockFilterChain();
        var res = run("POST", "/api/auth/refresh", "http://localhost:3000", chain);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"https://evil.example", "http://localhost:3000/", "HTTP://LOCALHOST:3000"})
    void missingOrForeignOriginIsRejected(String origin) throws Exception {
        var chain = new MockFilterChain();
        var res = run("POST", "/api/auth/logout", origin, chain);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void otherPathsAndMethodsAreNotChecked() throws Exception {
        var chain = new MockFilterChain();
        run("POST", "/api/auth/login", null, chain);
        assertThat(chain.getRequest()).isNotNull();
    }
}
