package dev.fascodes.ticketSystem.integration;

import jakarta.servlet.http.Cookie;
import dev.fascodes.ticketSystem.auth.refresh.RefreshTokenRepository;
import dev.fascodes.ticketSystem.auth.jwt.JwtService;
import dev.fascodes.ticketSystem.common.MutableClock;
import dev.fascodes.ticketSystem.common.TestKeys;
import dev.fascodes.ticketSystem.config.PostgresTestConfig;
import dev.fascodes.ticketSystem.config.TestClockConfig;
import dev.fascodes.ticketSystem.config.TestJwtKeysConfig;
import dev.fascodes.ticketSystem.user.entity.User;
import dev.fascodes.ticketSystem.user.repository.RoleRepository;
import dev.fascodes.ticketSystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PostgresTestConfig.class, TestClockConfig.class, TestJwtKeysConfig.class})
class AuthFlowIT {

    private static final String PASSWORD = "super-tajne-haslo-123";
    private static final String ORIGIN = "http://localhost:3000";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserRepository users;
    @Autowired
    RoleRepository roles;
    @Autowired
    RefreshTokenRepository refreshTokens;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    record Session(String accessToken, Cookie refreshCookie) {}

    // ---------- helpers ----------

    private static String randomIp() {
        var r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + (1 + r.nextInt(254));
    }

    private static RequestPostProcessor from(String ip) {
        return req -> { req.setRemoteAddr(ip); return req; };
    }

    private String body(String email, String password) throws Exception {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    private String registerUser() throws Exception {
        String email = "u-" + UUID.randomUUID() + "@test.local";
        mvc.perform(post("/api/auth/register").with(from(randomIp()))
                        .contentType(APPLICATION_JSON).content(body(email, PASSWORD)))
                .andExpect(status().isCreated());
        return email;
    }

    private String createUserWithRole(String role) {
        String email = "u-" + UUID.randomUUID() + "@test.local";
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setEnabled(true);
        u.getRoles().add(roles.findByName(role).orElseThrow());
        users.save(u);
        return email;
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").with(from(ip))
                .contentType(APPLICATION_JSON).content(body(email, password)));
    }

    private Session loginOk(String email) throws Exception {
        MvcResult res = login(email, PASSWORD, randomIp()).andExpect(status().isOk()).andReturn();
        return session(res);
    }

    private Session session(MvcResult res) throws Exception {
        String access = json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
        return new Session(access, res.getResponse().getCookie("refresh_token"));
    }

    private ResultActions refresh(Cookie cookie, String origin) throws Exception {
        var req = post("/api/auth/refresh").with(from(randomIp())).cookie(cookie);
        if (origin != null) req.header("Origin", origin);
        return mvc.perform(req);
    }

    // ---------- scenariusze ----------

    @Test
    void registerLoginAndAccessProtectedEndpoint() throws Exception {
        String email = registerUser();
        Session s = loginOk(email);

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + s.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void refreshCookieHasHardenedAttributes() throws Exception {
        String email = registerUser();
        MvcResult res = login(email, PASSWORD, randomIp()).andExpect(status().isOk()).andReturn();

        assertThat(res.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
        assertThat(res.getResponse().getContentAsString()).doesNotContain("refresh");
    }

    @Test
    void apiResponsesCarrySecurityHeaders() throws Exception {
        Session s = loginOk(registerUser());

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + s.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    @Test
    void jwksExposesActiveAndRetiredPublicKeysOnly() throws Exception {
        MvcResult res = mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")))
                .andReturn();

        var keys = json.readTree(res.getResponse().getContentAsString()).get("keys");
        assertThat(keys).hasSize(2);
        for (var k : keys) {
            assertThat(k.get("kty").asText()).isEqualTo("RSA");
            assertThat(k.get("alg").asText()).isEqualTo("RS256");
            assertThat(k.has("d")).isFalse();
        }
        var active = java.util.stream.StreamSupport.stream(keys.spliterator(), false)
                .filter(k -> k.get("kid").asText().equals(TestKeys.ACTIVE_KID)).findFirst().orElseThrow();
        var modulus = new java.math.BigInteger(1, java.util.Base64.getUrlDecoder().decode(active.get("n").asText()));
        assertThat(modulus).isEqualTo(((java.security.interfaces.RSAPublicKey) TestKeys.PAIR.getPublic()).getModulus());
    }

    @Test
    void tokenSignedWithRetiredKeyStillAuthenticates() throws Exception {
        User user = users.findByEmail(registerUser()).orElseThrow();
        JwtService beforeRotation = new JwtService(
                TestKeys.ring(TestKeys.RETIRED_KID, TestKeys.RETIRED, Map.of(TestKeys.RETIRED_KID, TestKeys.RETIRED)),
                TestKeys.props("https://test.local", Duration.ofMinutes(10)), clock);
        String oldToken = beforeRotation.generateAccessToken(user.getId(), user.getEmail(), List.of("ROLE_USER"));

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutOrWithBrokenTokenReturnsJson401() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        Session s = loginOk(registerUser());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + s.accessToken() + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotReachAdminEndpointButAdminCan() throws Exception {
        Session user = loginOk(registerUser());
        Session admin = loginOk(createUserWithRole("ROLE_ADMIN"));

        mvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/ping").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void refreshRotatesTokenAndIssuesNewAccessToken() throws Exception {
        Session s1 = loginOk(registerUser());

        Session s2 = session(refresh(s1.refreshCookie(), ORIGIN).andExpect(status().isOk()).andReturn());

        assertThat(s2.refreshCookie().getValue()).isNotEqualTo(s1.refreshCookie().getValue());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + s2.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void reuseOfRotatedRefreshTokenRevokesWholeFamily() throws Exception {
        String email = registerUser();
        Session s1 = loginOk(email);
        Session s2 = session(refresh(s1.refreshCookie(), ORIGIN).andExpect(status().isOk()).andReturn());

        MvcResult reuse = refresh(s1.refreshCookie(), ORIGIN)
                .andExpect(status().isUnauthorized()).andReturn();
        refresh(s2.refreshCookie(), ORIGIN).andExpect(status().isUnauthorized());

        assertThat(reuse.getResponse().getHeader("Set-Cookie"))
                .contains("refresh_token=", "Max-Age=0", "Path=/api/auth");

        User user = users.findByEmail(email).orElseThrow();
        assertThat(refreshTokens.findAllByUserAndRevokedFalse(user)).isEmpty();
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Session s = loginOk(registerUser());

        mvc.perform(post("/api/auth/logout").with(from(randomIp()))
                        .cookie(s.refreshCookie()).header("Origin", ORIGIN))
                .andExpect(status().isNoContent());

        refresh(s.refreshCookie(), ORIGIN).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRequiresAllowedOrigin() throws Exception {
        Session s = loginOk(registerUser());
        refresh(s.refreshCookie(), null).andExpect(status().isForbidden());
        refresh(s.refreshCookie(), "https://evil.example").andExpect(status().isForbidden());
        refresh(s.refreshCookie(), ORIGIN).andExpect(status().isOk()); // token nie został zużyty przez odrzucone requesty
    }

    @Test
    void wrongPasswordAndUnknownUserLookIdentical() throws Exception {
        String email = registerUser();

        MvcResult wrongPassword = login(email, "zle-haslo-123456", randomIp())
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult unknownUser = login("n-" + UUID.randomUUID() + "@test.local", "zle-haslo-123456", randomIp())
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(withoutTimestamp(wrongPassword)).isEqualTo(withoutTimestamp(unknownUser));
    }

    private ObjectNode withoutTimestamp(MvcResult res) throws Exception {
        ObjectNode body = (ObjectNode) json.readTree(res.getResponse().getContentAsString());
        body.remove("timestamp");
        return body;
    }

    // TODO: Here add a test to check if multiple threads try to login at once
    //  (could be different IPs due to rate limiting) the attempts should only go up to max before lock
    //  also checks if locking works

    @Test
    void accountLocksAfterFiveFailuresAndUnlocksAfterExpiry() throws Exception {
        String email = registerUser();

        for (int i = 0; i < 5; i++) {
            login(email, "zle-haslo-123456", randomIp()).andExpect(status().isUnauthorized());
        }
        login(email, PASSWORD, randomIp()).andExpect(status().isUnauthorized());

        assertThat(users.findByEmail(email).orElseThrow().getLockedUntil())
                .isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));

        clock.advance(Duration.ofMinutes(14));
        login(email, PASSWORD, randomIp()).andExpect(status().isUnauthorized());

        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        login(email, PASSWORD, randomIp()).andExpect(status().isOk());
        assertThat(users.findByEmail(email).orElseThrow().getFailedLoginAttempts()).isZero();
    }

    @Test
    void loginIsRateLimitedPerIp() throws Exception {
        String ip = randomIp();
        for (int i = 0; i < 10; i++) {
            login("nikt@test.local", "cokolwiek123456", ip).andExpect(status().isUnauthorized());
        }
        login("nikt@test.local", "cokolwiek123456", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "6")); // 10 tokenów / min -> jeden co 6 s

        clock.advance(Duration.ofSeconds(6));
        login("nikt@test.local", "cokolwiek123456", ip).andExpect(status().isUnauthorized());
    }
}
