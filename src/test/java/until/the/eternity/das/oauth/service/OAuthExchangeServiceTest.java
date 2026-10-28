package until.the.eternity.das.oauth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.oauth.dto.OauthUserDTO;

class OAuthExchangeServiceTest {
    private OAuthExchangeService service;
    private JdbcTemplate jdbc;
    private Instant now;

    @BeforeEach
    void setUp() {
        String database = "oauth_" + UUID.randomUUID().toString().replace("-", "");
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                "jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                                "sa",
                                ""));
        jdbc.execute(
                """
      CREATE TABLE oauth_exchange_flows (
        id BIGINT AUTO_INCREMENT PRIMARY KEY, code_hash CHAR(64) NOT NULL UNIQUE,
        proof_hash CHAR(64) UNIQUE, binding_hash CHAR(64) NOT NULL,
        flow_type VARCHAR(16) NOT NULL, provider VARCHAR(32), provider_user_id VARCHAR(255),
        email VARCHAR(255), code_expires_at_ms BIGINT NOT NULL,
        code_consumed_at_ms BIGINT, proof_expires_at_ms BIGINT,
        proof_consumed_at_ms BIGINT, created_at_ms BIGINT NOT NULL
      )
      """);
        now = Instant.parse("2026-10-27T00:00:00Z");
        service = new OAuthExchangeService(jdbc, Clock.fixed(now, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "codeValiditySeconds", 300L);
        ReflectionTestUtils.setField(service, "proofValiditySeconds", 300L);
    }

    @Test
    void signupCodeAndProofAreBoundAndEachConsumedOnce() {
        String binding = service.newBinding();
        String otherBinding = service.newBinding();
        String code =
                service.issueSignupCode(
                        new OauthUserDTO("verified-id", "social@example.invalid", "GOOGLE"),
                        binding);

        assertCode(
                GlobalExceptionCode.OAUTH_EXCHANGE_INVALID,
                () -> service.exchange(code, otherBinding));
        OAuthExchangeService.ExchangeResult exchanged = service.exchange(code, binding);
        assertEquals("SIGNUP_REQUIRED", exchanged.type());
        assertEquals("GOOGLE", exchanged.provider());
        assertEquals("social@example.invalid", exchanged.email());
        assertCode(
                GlobalExceptionCode.OAUTH_EXCHANGE_INVALID, () -> service.exchange(code, binding));
        assertCode(
                GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID,
                () -> service.consumeSignupProof(exchanged.signupProof(), otherBinding));

        OauthUserDTO identity = service.consumeSignupProof(exchanged.signupProof(), binding);
        assertEquals("verified-id", identity.getProviderUserId());
        assertCode(
                GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID,
                () -> service.consumeSignupProof(exchanged.signupProof(), binding));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM oauth_exchange_flows WHERE code_hash = ?",
                        Integer.class,
                        code));
    }

    @Test
    void loginExchangeReturnsNoIdentity() {
        String binding = service.newBinding();
        String code = service.issueLoginCode(binding);
        OAuthExchangeService.ExchangeResult exchanged = service.exchange(code, binding);
        assertEquals("LOGIN_SUCCESS", exchanged.type());
        assertEquals(null, exchanged.email());
        assertEquals(null, exchanged.signupProof());
    }

    @Test
    void expiredCodeAndProofFailClosed() {
        String binding = service.newBinding();
        assertCode(
                GlobalExceptionCode.OAUTH_EXCHANGE_INVALID, () -> service.exchange(null, binding));
        assertCode(
                GlobalExceptionCode.OAUTH_SIGNUP_PROOF_REQUIRED,
                () -> service.consumeSignupProof(null, binding));
        String code =
                service.issueSignupCode(
                        new OauthUserDTO("verified-id", "social@example.invalid", "GOOGLE"),
                        binding);
        jdbc.update(
                "UPDATE oauth_exchange_flows SET code_expires_at_ms = ?",
                now.minusSeconds(1).toEpochMilli());
        assertCode(
                GlobalExceptionCode.OAUTH_EXCHANGE_INVALID, () -> service.exchange(code, binding));

        String secondCode =
                service.issueSignupCode(
                        new OauthUserDTO("verified-id", "social@example.invalid", "GOOGLE"),
                        binding);
        String proof = service.exchange(secondCode, binding).signupProof();
        jdbc.update(
                "UPDATE oauth_exchange_flows SET proof_expires_at_ms = ? WHERE proof_hash IS NOT NULL",
                now.minusSeconds(1).toEpochMilli());
        assertCode(
                GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID,
                () -> service.consumeSignupProof(proof, binding));
    }

    @Test
    void onlyOneConcurrentProofConsumerCanWin() throws Exception {
        String binding = service.newBinding();
        String code =
                service.issueSignupCode(
                        new OauthUserDTO("verified-id", "social@example.invalid", "GOOGLE"),
                        binding);
        String proof = service.exchange(code, binding).signupProof();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> consumeAfter(start, proof, binding));
            var second = executor.submit(() -> consumeAfter(start, proof, binding));
            start.countDown();
            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
        }
    }

    @Test
    void cleanupDeletesOnlyFlowsExpiredBeyondRetention() {
        String oldBinding = service.newBinding();
        service.issueSignupCode(
                new OauthUserDTO("old-id", "old@example.invalid", "GOOGLE"), oldBinding);
        jdbc.update(
                "UPDATE oauth_exchange_flows SET code_expires_at_ms = ?",
                now.minusSeconds(7_200).toEpochMilli());
        service.issueLoginCode(service.newBinding());

        service.cleanupExpired();
        assertEquals(
                1, jdbc.queryForObject("SELECT COUNT(*) FROM oauth_exchange_flows", Integer.class));
    }

    private boolean consumeAfter(CountDownLatch start, String proof, String binding)
            throws Exception {
        start.await();
        try {
            service.consumeSignupProof(proof, binding);
            return true;
        } catch (CustomException exception) {
            assertEquals(GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID, exception.getCode());
            return false;
        }
    }

    private void assertCode(GlobalExceptionCode expected, Runnable action) {
        assertEquals(expected, assertThrows(CustomException.class, action::run).getCode());
    }
}
