package until.the.eternity.das.oauth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.oauth.dto.OauthUserDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OAuthExchangeService {
  public static final String BINDING_COOKIE = "oauth_flow_binding";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final JdbcTemplate jdbc;
  private final Clock clock;

  @Value("${app.oauth.code-validity-seconds:300}")
  private long codeValiditySeconds;

  @Value("${app.oauth.proof-validity-seconds:300}")
  private long proofValiditySeconds;

  public String newBinding() {
    return randomToken();
  }

  @Transactional
  public String issueLoginCode(String binding) {
    return issue("LOGIN_SUCCESS", null, binding);
  }

  @Transactional
  public String issueSignupCode(OauthUserDTO identity, String binding) {
    return issue("SIGNUP_REQUIRED", identity, binding);
  }

  private String issue(String type, OauthUserDTO identity, String binding) {
    String code = randomToken();
    long now = clock.millis();
    jdbc.update("""
      INSERT INTO oauth_exchange_flows
      (code_hash, binding_hash, flow_type, provider, provider_user_id, email,
       code_expires_at_ms, created_at_ms)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      """, hash(code), hash(binding), type,
      identity == null ? null : identity.getProvider(),
      identity == null ? null : identity.getProviderUserId(),
      identity == null ? null : identity.getEmail(),
      now + codeValiditySeconds * 1000, now);
    return code;
  }

  @Transactional
  public ExchangeResult exchange(String code, String binding) {
    if (!validToken(code) || !validToken(binding)) {
      throw new CustomException(GlobalExceptionCode.OAUTH_EXCHANGE_INVALID);
    }
    String codeHash = hash(code);
    long now = clock.millis();
    int updated = jdbc.update("""
      UPDATE oauth_exchange_flows SET code_consumed_at_ms = ?
      WHERE code_hash = ? AND binding_hash = ? AND code_consumed_at_ms IS NULL
        AND code_expires_at_ms > ?
      """, now, codeHash, hash(binding), now);
    if (updated != 1) {
      throw new CustomException(GlobalExceptionCode.OAUTH_EXCHANGE_INVALID);
    }
    List<ExchangeRow> rows = jdbc.query("""
      SELECT flow_type, provider, email FROM oauth_exchange_flows WHERE code_hash = ?
      """, (rs, rowNum) -> new ExchangeRow(rs.getString("flow_type"),
      rs.getString("provider"), rs.getString("email")), codeHash);
    if (rows.size() != 1) {
      throw new CustomException(GlobalExceptionCode.OAUTH_EXCHANGE_INVALID);
    }
    ExchangeRow row = rows.getFirst();
    if ("LOGIN_SUCCESS".equals(row.type())) {
      return new ExchangeResult("LOGIN_SUCCESS", null, null, null);
    }
    if (!"SIGNUP_REQUIRED".equals(row.type())) {
      throw new CustomException(GlobalExceptionCode.OAUTH_EXCHANGE_INVALID);
    }
    String proof = randomToken();
    jdbc.update("""
      UPDATE oauth_exchange_flows
      SET proof_hash = ?, proof_expires_at_ms = ? WHERE code_hash = ?
      """, hash(proof), now + proofValiditySeconds * 1000, codeHash);
    return new ExchangeResult("SIGNUP_REQUIRED", row.provider(), row.email(), proof);
  }

  @Transactional
  public OauthUserDTO consumeSignupProof(String proof, String binding) {
    if (proof == null || proof.isBlank()) {
      throw new CustomException(GlobalExceptionCode.OAUTH_SIGNUP_PROOF_REQUIRED);
    }
    if (!validToken(proof) || !validToken(binding)) {
      throw new CustomException(GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID);
    }
    String proofHash = hash(proof);
    long now = clock.millis();
    int updated = jdbc.update("""
      UPDATE oauth_exchange_flows SET proof_consumed_at_ms = ?
      WHERE proof_hash = ? AND binding_hash = ? AND code_consumed_at_ms IS NOT NULL
        AND proof_consumed_at_ms IS NULL AND proof_expires_at_ms > ?
      """, now, proofHash, hash(binding), now);
    if (updated != 1) {
      throw new CustomException(GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID);
    }
    List<OauthUserDTO> rows = jdbc.query("""
      SELECT provider, provider_user_id, email FROM oauth_exchange_flows WHERE proof_hash = ?
      """, (rs, rowNum) -> new OauthUserDTO(rs.getString("provider_user_id"),
      rs.getString("email"), rs.getString("provider")), proofHash);
    if (rows.size() != 1) {
      throw new CustomException(GlobalExceptionCode.OAUTH_SIGNUP_PROOF_INVALID);
    }
    return rows.getFirst();
  }

  @Scheduled(initialDelayString = "${app.oauth.cleanup-interval-ms:600000}",
    fixedDelayString = "${app.oauth.cleanup-interval-ms:600000}")
  @Transactional
  public void cleanupExpired() {
    removeExpiredRows(clock.millis() - 3_600_000);
  }

  private void removeExpiredRows(long cutoff) {
    List<Long> ids = jdbc.query("""
      SELECT id FROM oauth_exchange_flows
      WHERE code_expires_at_ms < ? AND (proof_expires_at_ms IS NULL OR proof_expires_at_ms < ?)
      ORDER BY id LIMIT 100
      """, (rs, rowNum) -> rs.getLong("id"), cutoff, cutoff);
    for (Long id : ids) {
      jdbc.update("DELETE FROM oauth_exchange_flows WHERE id = ?", id);
    }
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static boolean validToken(String token) {
    if (token == null || token.length() != 43) {
      return false;
    }
    try {
      return Base64.getUrlDecoder().decode(token).length == 32;
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private static String hash(String value) {
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256")
        .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private record ExchangeRow(String type, String provider, String email) {}

  public record ExchangeResult(String type, String provider, String email, String signupProof) {}
}
