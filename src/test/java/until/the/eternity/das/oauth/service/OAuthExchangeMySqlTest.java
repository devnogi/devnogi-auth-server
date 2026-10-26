package until.the.eternity.das.oauth.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.oauth.dto.OauthUserDTO;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "NICKNAME_MYSQL_REQUIRED", matches = "true")
class OAuthExchangeMySqlTest {
  @Test
  void realMigrationAndConditionalUpdateAllowOnlyOneProofConsumer() throws Exception {
    String url = System.getenv("NICKNAME_MYSQL_URL");
    String user = System.getenv("NICKNAME_MYSQL_USER");
    String password = System.getenv("NICKNAME_MYSQL_PASSWORD");
    assertTrue(url != null && !url.isBlank() && user != null && password != null);
    Flyway.configure().dataSource(url, user, password)
      .locations("classpath:db/migration").load().migrate();

    JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
    OAuthExchangeService exchanges = new OAuthExchangeService(jdbc, Clock.systemUTC());
    ReflectionTestUtils.setField(exchanges, "codeValiditySeconds", 300L);
    ReflectionTestUtils.setField(exchanges, "proofValiditySeconds", 300L);
    String binding = exchanges.newBinding();
    String code = exchanges.issueSignupCode(
      new OauthUserDTO("mysql-verified-id", "mysql-oauth@example.invalid", "GOOGLE"), binding);
    String proof = exchanges.exchange(code, binding).signupProof();
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> consume(exchanges, start, proof, binding));
      var second = executor.submit(() -> consume(exchanges, start, proof, binding));
      start.countDown();
      assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
    }
  }

  private boolean consume(OAuthExchangeService exchanges, CountDownLatch start,
                          String proof, String binding) throws Exception {
    start.await();
    try {
      assertEquals("mysql-verified-id", exchanges.consumeSignupProof(proof, binding).getProviderUserId());
      return true;
    } catch (CustomException exception) {
      assertEquals(GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID, exception.getCode());
      return false;
    }
  }
}
