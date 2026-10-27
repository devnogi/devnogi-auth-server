package until.the.eternity.das.verification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.verification.kafka.UserVerificationVerifyMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class UserVerificationFailureHistoryMySqlTest {
  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("NICKNAME_MYSQL_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("NICKNAME_MYSQL_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("NICKNAME_MYSQL_PASSWORD"));
    properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    properties.add("spring.datasource.hikari.connection-init-sql", () -> "SET SESSION innodb_lock_wait_timeout=3");
    properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    properties.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private UserVerificationService verifications;
  @MockitoBean private NicknameCandidateService candidates;
  @MockitoBean private KafkaProducerService events;
  @MockitoBean private S3Service images;

  @Test
  void revokedTokenRecordsFailureWithoutWaitingOnItsOwnUserLock() throws Exception {
    assertFailureHistoryCommits(true, LocalDateTime.now().plusMinutes(30), "TOKEN_REVOKED");
  }

  @Test
  void expiredTokenRecordsFailureWithoutWaitingOnItsOwnUserLock() throws Exception {
    assertFailureHistoryCommits(false, LocalDateTime.now().minusMinutes(1), "TOKEN_EXPIRED");
  }

  private void assertFailureHistoryCommits(boolean revoked, LocalDateTime expiresAt, String reason) throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String email = "verification-history-" + suffix + "@example.invalid";
    String token = suffix;
    jdbc.update("INSERT INTO users (email, nickname, status, is_verified, role_id) "
      + "VALUES (?, ?, 'ACTIVE', FALSE, 3)", email, "인증실패" + suffix.substring(0, 12));
    Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    jdbc.update("INSERT INTO user_verification_token "
        + "(user_id, issued_at, expires_at, revoked, token_value, verified) "
        + "VALUES (?, ?, ?, ?, ?, FALSE)",
      userId, LocalDateTime.now().minusHours(2), expiresAt, revoked, token);
    Long tokenId = jdbc.queryForObject(
      "SELECT id FROM user_verification_token WHERE token_value = ?", Long.class, token);
    String payload = json.writeValueAsString(new UserVerificationVerifyMessage(
      "합성캐릭터", "류트", token, "verified", Instant.now()));

    assertTimeoutPreemptively(Duration.ofSeconds(12), () -> verifications.verifyFromKafkaPayload(payload));

    assertEquals(1, jdbc.queryForObject(
      "SELECT COUNT(*) FROM user_verification_history "
        + "WHERE user_id = ? AND token_id = ? AND failure_reason = ? AND verification_success = FALSE",
      Integer.class, userId, tokenId, reason));
  }
}
