package dev.fascodes.ticketSystem.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, RateLimitRule> rules;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Cache<String, TokenBucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(2)) // musi być dłuższe niż najdłuższy okres odnawiania
            .build();

    public RateLimitFilter(Map<String, RateLimitRule> rules, ObjectMapper objectMapper, Clock clock) {
        this.rules = rules;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !rules.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        RateLimitRule rule = rules.get(path);
        String key = path + "|" + request.getRemoteAddr();

        TokenBucket bucket = buckets.get(key, k -> new TokenBucket(rule, clock));
        long retryAfter = bucket.tryConsume();

        if (retryAfter > 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "status", 429,
                    "error", "Too Many Requests",
                    "message", "Too many requests, try again later"));
            return;
        }
        chain.doFilter(request, response);
    }
}
