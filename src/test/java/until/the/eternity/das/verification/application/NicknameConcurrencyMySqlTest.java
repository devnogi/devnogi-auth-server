package until.the.eternity.das.verification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.ExceptionCode;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.application.UserService;
import until.the.eternity.das.user.dto.request.UserInfoUpdateRequest;
import until.the.eternity.das.user.dto.response.UserInfoUpdateEvent;
import until.the.eternity.das.verification.kafka.UserVerificationVerifyMessage;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class NicknameConcurrencyMySqlTest {
    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("NICKNAME_MYSQL_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("NICKNAME_MYSQL_USER"));
        properties.add(
                "spring.datasource.password", () -> System.getenv("NICKNAME_MYSQL_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserService users;
    @Autowired private UserVerificationService verifications;
    @Autowired private ObjectMapper json;
    @MockitoBean private NicknameCandidateService candidates;
    @MockitoBean private KafkaProducerService events;
    @MockitoBean private S3Service images;

    @BeforeEach
    void resetMocks() {
        reset(candidates, events, images);
    }

    @Test
    void verificationAfterConcurrentPageChangeWinsWithCharacterIdentity() throws Exception {
        Long userId = createUnverifiedUser("강인한 인간 ZZZ01");
        String token = createVerificationToken(userId);
        String character = "동시인증캐릭터1";
        String candidate = "강인한 인간 ZZZ02";
        CountDownLatch pageHoldingRowLock = new CountDownLatch(1);
        CountDownLatch releasePage = new CountDownLatch(1);
        doAnswer(
                        call -> {
                            pageHoldingRowLock.countDown();
                            assertTrue(releasePage.await(15, TimeUnit.SECONDS));
                            return null;
                        })
                .when(candidates)
                .validateForChange(eq(candidate), eq("test-ticket"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> page = executor.submit(() -> changeNicknameAsUser(userId, candidate));
            assertTrue(pageHoldingRowLock.await(10, TimeUnit.SECONDS));
            Future<?> verification =
                    executor.submit(
                            () ->
                                    verifications.verifyFromKafkaPayload(
                                            verificationPayload(character, token)));
            Thread.sleep(250);
            assertFalse(verification.isDone(), "Verification must wait for the profile row lock");

            releasePage.countDown();
            assertTrue(page.get(15, TimeUnit.SECONDS));
            verification.get(15, TimeUnit.SECONDS);
            assertVerifiedCharacter(userId, character);
        } finally {
            releasePage.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void pageChangeAfterConcurrentVerificationCannotOverwriteCharacterIdentity() throws Exception {
        Long userId = createUnverifiedUser("강인한 인간 ZZZ03");
        String token = createVerificationToken(userId);
        String character = "동시인증캐릭터2";
        CountDownLatch verificationHoldingRowLock = new CountDownLatch(1);
        CountDownLatch releaseVerification = new CountDownLatch(1);
        doAnswer(
                        call -> {
                            UserInfoUpdateEvent event = call.getArgument(0);
                            if (character.equals(event.nickname())) {
                                verificationHoldingRowLock.countDown();
                                assertTrue(releaseVerification.await(15, TimeUnit.SECONDS));
                            }
                            return null;
                        })
                .when(events)
                .sendUserInfoUpdateEvent(any(UserInfoUpdateEvent.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> verification =
                    executor.submit(
                            () ->
                                    verifications.verifyFromKafkaPayload(
                                            verificationPayload(character, token)));
            assertTrue(verificationHoldingRowLock.await(10, TimeUnit.SECONDS));
            Future<ExceptionCode> page =
                    executor.submit(
                            () -> {
                                try {
                                    changeNicknameAsUser(userId, "강인한 인간 ZZZ04");
                                    return null;
                                } catch (CustomException e) {
                                    return e.getCode();
                                }
                            });
            Thread.sleep(250);
            assertFalse(page.isDone(), "Profile update must wait for the verification row lock");

            releaseVerification.countDown();
            verification.get(15, TimeUnit.SECONDS);
            assertEquals(
                    GlobalExceptionCode.USER_VERIFICATION_REQUIRED_FOR_IDENTITY_UPDATE,
                    page.get(15, TimeUnit.SECONDS));
            assertVerifiedCharacter(userId, character);
        } finally {
            releaseVerification.countDown();
            executor.shutdownNow();
        }
    }

    private Boolean changeNicknameAsUser(Long userId, String nickname) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        try {
            return users.updateUserInfo(
                    new UserInfoUpdateRequest(nickname, "test-ticket", null), userId);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Long createUnverifiedUser(String nickname) {
        String email = "nickname-race-" + UUID.randomUUID() + "@example.invalid";
        jdbc.update(
                "INSERT INTO users (email, nickname, status, is_verified, role_id) "
                        + "VALUES (?, ?, 'ACTIVE', FALSE, 3)",
                email,
                nickname);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private String createVerificationToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        jdbc.update(
                "INSERT INTO user_verification_token "
                        + "(user_id, issued_at, expires_at, revoked, token_value, verified) "
                        + "VALUES (?, ?, ?, FALSE, ?, FALSE)",
                userId,
                now.minusMinutes(1),
                now.plusMinutes(30),
                token);
        return token;
    }

    private String verificationPayload(String character, String token) {
        try {
            return json.writeValueAsString(
                    new UserVerificationVerifyMessage(
                            character, "류트", token, "verified", Instant.now()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertVerifiedCharacter(Long userId, String character) {
        assertEquals(
                character,
                jdbc.queryForObject(
                        "SELECT nickname FROM users WHERE id = ?", String.class, userId));
        assertEquals(
                Boolean.TRUE,
                jdbc.queryForObject(
                        "SELECT is_verified FROM users WHERE id = ?", Boolean.class, userId));
        assertEquals(
                "류트",
                jdbc.queryForObject(
                        "SELECT server_name FROM users WHERE id = ?", String.class, userId));
    }
}
