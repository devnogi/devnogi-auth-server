package until.the.eternity.das.verification.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.application.UserService;
import until.the.eternity.das.verification.entity.UserVerificationRepository;
import until.the.eternity.das.verification.entity.UserVerificationTokenRepository;
import until.the.eternity.das.verification.kafka.UserVerificationVerifyMessage;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class UserVerificationConcurrencyMySqlTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("NICKNAME_MYSQL_URL"));
        p.add("spring.datasource.username", () -> System.getenv("NICKNAME_MYSQL_USER"));
        p.add("spring.datasource.password", () -> System.getenv("NICKNAME_MYSQL_PASSWORD"));
        p.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper json;
    @Autowired UserVerificationService service;
    @MockitoSpyBean UserVerificationTokenRepository tokens;
    @MockitoSpyBean UserVerificationRepository verifications;
    @MockitoSpyBean UserVerificationAttemptService attempts;
    @MockitoBean KafkaProducerService producer;
    @MockitoBean UserService users;
    @MockitoBean NicknameCandidateService candidates;
    @MockitoBean S3Service images;

    @BeforeEach
    void setup() {
        reset(tokens, verifications, attempts, producer, users);
        when(users.generateRandomNickname())
                .thenAnswer(i -> "대체" + UUID.randomUUID().toString().substring(0, 12));
    }

    @Test
    void concurrentIssueAndReissueLeaveOnlyOneUsableToken() throws Exception {
        for (int n = 0; n < 5; n++) {
            long userId = user();
            List<Object> issue = race(() -> issue(userId, false), () -> issue(userId, false));
            assertEquals(1, issue.stream().filter(x -> !(x instanceof CustomException)).count());
            assertEquals(1, active(userId));
            List<Object> reissue = race(() -> issue(userId, true), () -> issue(userId, true));
            assertTrue(reissue.stream().noneMatch(x -> x instanceof Exception));
            assertEquals(1, active(userId));
        }
    }

    @Test
    void latestTokenUsesIdToBreakSameTimestampTie() {
        long id = user();
        LocalDateTime issued = LocalDateTime.now().withNano(0);
        String oldValue = UUID.randomUUID().toString().replace("-", "");
        String newValue = UUID.randomUUID().toString().replace("-", "");
        jdbc.update(
                "INSERT INTO user_verification_token(user_id,issued_at,expires_at,revoked,token_value,verified) VALUES (?,?,?,true,?,false)",
                id,
                issued,
                issued.plusMinutes(30),
                oldValue);
        jdbc.update(
                "INSERT INTO user_verification_token(user_id,issued_at,expires_at,revoked,token_value,verified) VALUES (?,?,?,false,?,false)",
                id,
                issued,
                issued.plusMinutes(30),
                newValue);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(id, null, List.of()));
        try {
            var result = service.getMyToken(id);
            assertTrue(result.verificationCode().endsWith(newValue));
            assertFalse(result.revoked());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void duplicatePayloadIsConsumedOnceWithAndWithoutExistingSummary() throws Exception {
        for (boolean existing : new boolean[] {false, true}) {
            for (int n = 0; n < 5; n++) {
                long userId = user();
                String token = token(userId);
                if (existing)
                    jdbc.update(
                            "INSERT INTO user_verification (user_id,is_verified,verification_count) VALUES (?,false,0)",
                            userId);
                String payload =
                        payload("검증" + UUID.randomUUID().toString().substring(0, 8), token);
                List<Object> results =
                        race(() -> verifyPayload(payload), () -> verifyPayload(payload));
                assertTrue(
                        results.stream().noneMatch(x -> x instanceof Exception),
                        results.toString());
                assertEquals(
                        1,
                        jdbc.queryForObject(
                                "SELECT verification_count FROM user_verification WHERE user_id=?",
                                Integer.class,
                                userId));
                assertEquals(
                        1,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM user_verification_history WHERE user_id=? AND verification_success=true",
                                Integer.class,
                                userId));
                assertEquals(
                        1,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM user_verification_history WHERE user_id=? AND failure_reason='TOKEN_ALREADY_VERIFIED'",
                                Integer.class,
                                userId));
            }
        }
    }

    @Test
    void verificationRevokesOtherPreviouslyIssuedTokensAndEnforcesCooldown() throws Exception {
        long id = user();
        String first = token(id);
        String second = token(id);
        verifyPayload(payload("단일" + UUID.randomUUID().toString().substring(0, 8), first));
        assertEquals(0, active(id));
        verifyPayload(payload("다른" + UUID.randomUUID().toString().substring(0, 8), second));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT verification_count FROM user_verification WHERE user_id=?",
                        Integer.class,
                        id));
        Object issue = issue(id, false);
        assertTrue(issue instanceof CustomException);
        assertEquals(
                GlobalExceptionCode.USER_VERIFICATION_COOLDOWN_ACTIVE,
                ((CustomException) issue).getCode());
    }

    @Test
    void verificationFirstMakesConcurrentReissueFailWithCooldown() throws Exception {
        long id = user();
        String value = token(id);
        String character = "선인증" + UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch publishing = new CountDownLatch(1),
                release = new CountDownLatch(1),
                started = new CountDownLatch(1);
        doAnswer(
                        call -> {
                            publishing.countDown();
                            assertTrue(release.await(15, TimeUnit.SECONDS));
                            return null;
                        })
                .when(producer)
                .sendUserInfoUpdateEvent(any());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> verification =
                    pool.submit(() -> verifyPayload(payload(character, value)));
            assertTrue(publishing.await(10, TimeUnit.SECONDS));
            Future<Object> reissue =
                    pool.submit(
                            () -> {
                                started.countDown();
                                return issue(id, true);
                            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertFalse(verification.get(15, TimeUnit.SECONDS) instanceof Exception);
            Object denied = reissue.get(15, TimeUnit.SECONDS);
            assertTrue(denied instanceof CustomException);
            assertEquals(
                    GlobalExceptionCode.USER_VERIFICATION_COOLDOWN_ACTIVE,
                    ((CustomException) denied).getCode());
            assertEquals(0, active(id));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT verification_count FROM user_verification WHERE user_id=?",
                            Integer.class,
                            id));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void reissueFirstRevokesOldProofAndNewProofCanBeConsumedOnce() throws Exception {
        long id = user();
        String oldToken = token(id);
        String character = "선재발급" + UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch locked = new CountDownLatch(1),
                release = new CountDownLatch(1),
                started = new CountDownLatch(1);
        doAnswer(
                        call -> {
                            locked.countDown();
                            assertTrue(release.await(15, TimeUnit.SECONDS));
                            return false;
                        })
                .when(tokens)
                .existsByTokenValue(anyString());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> reissue = pool.submit(() -> issue(id, true));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<Object> oldProof =
                    pool.submit(
                            () -> {
                                started.countDown();
                                return verifyPayload(payload(character, oldToken));
                            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertFalse(reissue.get(15, TimeUnit.SECONDS) instanceof Exception);
            assertFalse(oldProof.get(15, TimeUnit.SECONDS) instanceof Exception);
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM user_verification_history WHERE user_id=? AND failure_reason='TOKEN_REVOKED'",
                            Integer.class,
                            id));
            assertEquals(1, active(id));
            String newToken =
                    jdbc.queryForObject(
                            "SELECT token_value FROM user_verification_token WHERE user_id=? AND revoked=false AND verified=false",
                            String.class,
                            id);
            reset(tokens);
            assertFalse(verifyPayload(payload(character, newToken)) instanceof Exception);
            assertEquals(0, active(id));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT verification_count FROM user_verification WHERE user_id=?",
                            Integer.class,
                            id));
            assertEquals(
                    GlobalExceptionCode.USER_VERIFICATION_COOLDOWN_ACTIVE,
                    ((CustomException) issue(id, true)).getCode());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void differentUsersClaimSameCharacterOrEachOthersCharacters() throws Exception {
        for (int n = 0; n < 5; n++) {
            long a = user(), b = user();
            String target = "공통" + UUID.randomUUID().toString().substring(0, 8);
            String aToken = token(a), bToken = token(b);
            List<Object> same =
                    race(
                            () -> verifyPayload(payload(target, aToken)),
                            () -> verifyPayload(payload(target, bToken)));
            assertTrue(same.stream().noneMatch(x -> x instanceof Exception), same.toString());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM users WHERE server_name='류트' AND nickname=?",
                            Integer.class,
                            target));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM user_verification WHERE server_name='류트' AND character_name=? AND is_verified=true",
                            Integer.class,
                            target));

            long c = user(), d = user();
            String charC = "교환C" + UUID.randomUUID().toString().substring(0, 8);
            String charD = "교환D" + UUID.randomUUID().toString().substring(0, 8);
            seedVerified(c, charC);
            seedVerified(d, charD);
            String cToken = token(c), dToken = token(d);
            List<Object> crossing =
                    race(
                            () -> verifyPayload(payload(charD, cToken)),
                            () -> verifyPayload(payload(charC, dToken)));
            assertTrue(
                    crossing.stream().noneMatch(x -> x instanceof Exception), crossing.toString());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM users WHERE server_name='류트' AND nickname=?",
                            Integer.class,
                            charC));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM users WHERE server_name='류트' AND nickname=?",
                            Integer.class,
                            charD));
        }
    }

    @Test
    void tokenExpiredWhileWaitingForUserLockIsRejected() throws Exception {
        long id = user();
        String value = token(id);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try {
                long blockerId;
                try (var statement = lock.createStatement();
                        var result = statement.executeQuery("SELECT CONNECTION_ID()")) {
                    assertTrue(result.next());
                    blockerId = result.getLong(1);
                }
                try (var statement =
                        lock.prepareStatement("SELECT id FROM users WHERE id=? FOR UPDATE")) {
                    statement.setLong(1, id);
                    statement.executeQuery().close();
                }
                Future<Object> result =
                        executor.submit(() -> verifyPayload(payload("만료" + id, value)));
                awaitUserRowLockWait(id, blockerId);
                try (var statement =
                        lock.prepareStatement(
                                "UPDATE user_verification_token SET expires_at=DATE_SUB(NOW(), INTERVAL 1 MINUTE) "
                                        + "WHERE token_value=?")) {
                    statement.setString(1, value);
                    assertEquals(1, statement.executeUpdate());
                }
                lock.commit();
                assertFalse(result.get(15, TimeUnit.SECONDS) instanceof Exception);
                assertEquals(
                        1,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM user_verification_history WHERE user_id=? AND failure_reason='TOKEN_EXPIRED'",
                                Integer.class,
                                id));
                assertEquals(
                        0,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM user_verification WHERE user_id=?",
                                Integer.class,
                                id));
                verifyNoInteractions(producer);
            } finally {
                lock.rollback();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitUserRowLockWait(long userId, long blockerConnectionId)
            throws InterruptedException {
        String query =
                "SELECT COUNT(*) FROM performance_schema.data_lock_waits waits "
                        + "JOIN performance_schema.data_locks requested ON requested.ENGINE = waits.ENGINE "
                        + "AND requested.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID "
                        + "JOIN performance_schema.data_locks blocking ON blocking.ENGINE = waits.ENGINE "
                        + "AND blocking.ENGINE_LOCK_ID = waits.BLOCKING_ENGINE_LOCK_ID "
                        + "JOIN performance_schema.threads blocker ON blocker.THREAD_ID = blocking.THREAD_ID "
                        + "WHERE requested.OBJECT_SCHEMA = DATABASE() AND requested.OBJECT_NAME = 'users' "
                        + "AND requested.INDEX_NAME = 'PRIMARY' AND requested.LOCK_TYPE = 'RECORD' "
                        + "AND requested.LOCK_STATUS = 'WAITING' AND requested.LOCK_DATA = ? "
                        + "AND blocker.PROCESSLIST_ID = ?";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            Integer waits =
                    jdbc.queryForObject(
                            query, Integer.class, Long.toString(userId), blockerConnectionId);
            if (waits != null && waits > 0) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        fail(
                "Could not observe the target users PRIMARY row lock wait in performance_schema within 10 seconds");
    }

    @Test
    void changedOwnerRetriesThreeTimesWithoutPublishingOrChangingDatabase() throws Exception {
        long id = user();
        String value = token(id);
        doThrow(new UserVerificationAttemptService.OwnerChangedException())
                .when(attempts)
                .execute(any(), anyString(), eq(id), any(), any());
        Object result = verifyPayload(payload("충돌" + id, value));
        assertTrue(result instanceof CustomException);
        assertEquals(
                GlobalExceptionCode.USER_VERIFICATION_INVALID,
                ((CustomException) result).getCode());
        verify(attempts, times(3)).execute(any(), eq(value), eq(id), any(), any());
        verifyNoInteractions(producer);
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM user_verification WHERE user_id=?",
                        Integer.class,
                        id));
    }

    @Test
    void ownerChangeAfterDiscoveryRestartsBeforePublishing() throws Exception {
        long claimant = user(), incumbent = user();
        String value = token(claimant);
        String character = "소유자" + UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch discovered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(
                        call -> {
                            Optional<Long> owner =
                                    jdbc.query(
                                            "SELECT id FROM users WHERE server_name=? AND nickname=?",
                                            rs ->
                                                    rs.next()
                                                            ? Optional.of(rs.getLong(1))
                                                            : Optional.empty(),
                                            "류트",
                                            character);
                            if (calls.incrementAndGet() == 1) {
                                discovered.countDown();
                                assertTrue(release.await(15, TimeUnit.SECONDS));
                            }
                            return owner;
                        })
                .when(verifications)
                .findProfileOwnerId("류트", character);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Object> result = pool.submit(() -> verifyPayload(payload(character, value)));
            assertTrue(discovered.await(10, TimeUnit.SECONDS));
            seedVerified(incumbent, character);
            release.countDown();
            assertFalse(result.get(20, TimeUnit.SECONDS) instanceof Exception);
            assertTrue(calls.get() >= 4);
            verify(producer, times(2)).sendUserInfoUpdateEvent(any());
            assertEquals(
                    claimant,
                    jdbc.queryForObject(
                            "SELECT id FROM users WHERE server_name='류트' AND nickname=?",
                            Long.class,
                            character));
            assertEquals(
                    Boolean.FALSE,
                    jdbc.queryForObject(
                            "SELECT is_verified FROM user_verification WHERE user_id=?",
                            Boolean.class,
                            incumbent));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private long user() {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO users(email,nickname,status,is_verified,role_id) VALUES (?,?,'ACTIVE',false,3)",
                id + "@example.invalid",
                "무작위" + id.substring(0, 12));
        return jdbc.queryForObject(
                "SELECT id FROM users WHERE email=?", Long.class, id + "@example.invalid");
    }

    private String token(long id) {
        String value = UUID.randomUUID().toString().replace("-", "");
        jdbc.update(
                "INSERT INTO user_verification_token(user_id,issued_at,expires_at,revoked,token_value,verified) VALUES (?,?,?,false,?,false)",
                id,
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(30),
                value);
        return value;
    }

    private void seedVerified(long id, String character) {
        jdbc.update(
                "UPDATE users SET nickname=?,server_name='류트',is_verified=true WHERE id=?",
                character,
                id);
        jdbc.update(
                "INSERT INTO user_verification(user_id,server_name,character_name,is_verified,verification_count,last_verified_at) VALUES (?,'류트',?,true,1,?)",
                id,
                character,
                LocalDateTime.now().minusDays(10));
    }

    private int active(long id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM user_verification_token WHERE user_id=? AND revoked=false AND verified=false AND expires_at>now()",
                Integer.class,
                id);
    }

    private String payload(String character, String token) throws Exception {
        return json.writeValueAsString(
                new UserVerificationVerifyMessage(character, "류트", token, "test", Instant.now()));
    }

    private Object issue(long id, boolean reissue) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(id, null, List.of()));
        try {
            return reissue ? service.reissueToken(id) : service.issueToken(id);
        } catch (Exception e) {
            return e;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Object verifyPayload(String payload) {
        try {
            service.verifyFromKafkaPayload(payload);
            return Boolean.TRUE;
        } catch (Exception e) {
            return e;
        }
    }

    private List<Object> race(
            java.util.concurrent.Callable<Object> a, java.util.concurrent.Callable<Object> b)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        try {
            Future<Object> first =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                assertTrue(go.await(10, TimeUnit.SECONDS));
                                return a.call();
                            });
            Future<Object> second =
                    pool.submit(
                            () -> {
                                ready.countDown();
                                assertTrue(go.await(10, TimeUnit.SECONDS));
                                return b.call();
                            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }
}
