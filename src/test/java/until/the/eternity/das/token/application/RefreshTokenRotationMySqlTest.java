package until.the.eternity.das.token.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class RefreshTokenRotationMySqlTest {
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("NICKNAME_MYSQL_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("NICKNAME_MYSQL_USER"));
        properties.add(
                "spring.datasource.password", () -> System.getenv("NICKNAME_MYSQL_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private TokenService tokens;
    @Autowired private JwtUtil jwt;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private NicknameCandidateService candidates;
    @MockitoBean private KafkaProducerService events;
    @MockitoBean private S3Service images;

    @Test
    void singleLegacyTokenRotatesOnceAndReplaysFail() {
        User user = createUser();
        String oldToken = legacyToken(user);
        store(user.getId(), oldToken);

        String replacement = tokens.refresh(oldToken).refreshToken();

        assertNotEquals(oldToken, replacement);
        assertTrue(jwt.extractAllClaims(replacement).getId() != null);
        assertEquals(1, activeCount(user.getId()));
        assertEquals(0, activeCount(oldToken));
        assertEquals(
                GlobalExceptionCode.INVALID_REFRESH_TOKEN,
                assertThrows(CustomException.class, () -> tokens.refresh(oldToken)).getCode());
    }

    @Test
    void duplicateLegacyBearerFailsClosedWithoutCreatingSuccessor() {
        User user = createUser();
        String duplicate = legacyToken(user);
        store(user.getId(), duplicate);
        store(user.getId(), duplicate);

        assertEquals(
                GlobalExceptionCode.INVALID_REFRESH_TOKEN,
                assertThrows(CustomException.class, () -> tokens.refresh(duplicate)).getCode());

        assertEquals(2, activeCount(user.getId()));
        assertEquals(2, activeCount(duplicate));
    }

    @Test
    void concurrentRefreshHasExactlyOneWinner() throws Exception {
        User user = createUser();
        String original = jwt.generateRefreshToken(user);
        store(user.getId(), original);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> refreshOnce(start, original));
            var second = executor.submit(() -> refreshOnce(start, original));
            start.countDown();
            assertEquals(
                    1,
                    (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                            + (second.get(20, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(0, activeCount(original));
        assertEquals(1, activeCount(user.getId()));
    }

    @Test
    void concurrentLogoutLeavesNoActiveRefreshSuccessor() throws Exception {
        User user = createUser();
        String original = jwt.generateRefreshToken(user);
        store(user.getId(), original);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var refresh = executor.submit(() -> refreshOnce(start, original));
            var logout =
                    executor.submit(
                            () -> {
                                start.await();
                                tokens.revokeAllUserTokens(user.getId());
                                return true;
                            });
            start.countDown();
            refresh.get(20, TimeUnit.SECONDS);
            assertTrue(logout.get(20, TimeUnit.SECONDS));
        }
        assertEquals(0, activeCount(user.getId()));
    }

    @Test
    void outerRollbackRestoresOriginalAndDiscardsNewToken() {
        User user = createUser();
        String original = jwt.generateRefreshToken(user);
        store(user.getId(), original);

        assertThrows(
                IntentionalRollback.class,
                () ->
                        transactions.executeWithoutResult(
                                status -> {
                                    tokens.refresh(original);
                                    throw new IntentionalRollback();
                                }));

        assertEquals(1, activeCount(user.getId()));
        assertEquals(1, activeCount(original));
    }

    private boolean refreshOnce(CountDownLatch start, String token) throws InterruptedException {
        start.await();
        try {
            tokens.refresh(token);
            return true;
        } catch (CustomException exception) {
            assertEquals(GlobalExceptionCode.INVALID_REFRESH_TOKEN, exception.getCode());
            return false;
        }
    }

    private User createUser() {
        jdbc.update("insert into roles(name) values ('USER') on duplicate key update id=id");
        Long roleId = jdbc.queryForObject("select id from roles where name='USER'", Long.class);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String email = "refresh-" + suffix + "@example.invalid";
        jdbc.update(
                "insert into users(email, password_hash, nickname, status, role_id, is_verified) "
                        + "values (?, 'synthetic-hash', ?, 'ACTIVE', ?, false)",
                email,
                "테스트 " + suffix,
                roleId);
        Long userId = jdbc.queryForObject("select id from users where email=?", Long.class, email);
        return users.findById(userId).orElseThrow();
    }

    private String legacyToken(User user) {
        byte[] key =
                Base64.getDecoder().decode("bWVtb25vZ2ktbG9jYWwtb25seS1zeW50aGV0aWMta2V5LTIwMjY=");
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("userId", user.getId())
                .claim("type", "REFRESH")
                .subject(user.getEmail())
                .issuer("contract-test")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(key))
                .compact();
    }

    private void store(Long userId, String token) {
        jdbc.update(
                "insert into refresh_tokens(user_id, token, expires_at, created_at, revoked) "
                        + "values (?, ?, date_add(now(), interval 1 day), now(), false)",
                userId,
                token);
    }

    private int activeCount(Long userId) {
        return jdbc.queryForObject(
                "select count(*) from refresh_tokens where user_id=? and revoked=false",
                Integer.class,
                userId);
    }

    private int activeCount(String token) {
        return jdbc.queryForObject(
                "select count(*) from refresh_tokens where token=? and revoked=false",
                Integer.class,
                token);
    }

    private static class IntentionalRollback extends RuntimeException {}
}
