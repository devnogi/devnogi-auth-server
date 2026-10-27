package until.the.eternity.das.verification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import until.the.eternity.das.common.aop.ActiveUserRequired;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.verification.dto.response.UserVerificationHistoryListResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationHistoryResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationInfoResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationPublicSummaryResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationTokenIssueResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationTokenResponse;
import until.the.eternity.das.verification.entity.UserVerification;
import until.the.eternity.das.verification.entity.UserVerificationHistoryRepository;
import until.the.eternity.das.verification.entity.UserVerificationRepository;
import until.the.eternity.das.verification.entity.UserVerificationToken;
import until.the.eternity.das.verification.entity.UserVerificationTokenRepository;
import until.the.eternity.das.verification.entity.enums.VerificationFailureReason;
import until.the.eternity.das.verification.entity.enums.VerificationTokenStatus;
import until.the.eternity.das.verification.kafka.UserVerificationVerifyMessage;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserVerificationService {

  private static final int MAX_GENERATION_RETRY = 20;
  private static final int NONCE_BYTES_LENGTH = 16;
  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final UserRepository userRepository;
  private final UserVerificationTokenRepository userVerificationTokenRepository;
  private final UserVerificationRepository userVerificationRepository;
  private final UserVerificationHistoryRepository userVerificationHistoryRepository;
  private final ObjectMapper objectMapper;

  private final SecureRandom secureRandom = new SecureRandom();
  private final UserVerificationHistoryService userVerificationHistoryService;
  private final EntityManager entityManager;
  private final UserVerificationAttemptService verificationAttemptService;

  @Value("${app.verification.prefix:메모노기_}")
  private String verificationPrefix;

  @Value("${app.verification.length:20}")
  private int verificationLength;

  @Value("${app.verification.validity-minutes:60}")
  private long verificationValidityMinutes;

  @Value("${app.verification.cooldown-days:7}")
  private long verificationCooldownDays;

  @Value("${app.verification.hash-secret:change-this-secret}")
  private String verificationHashSecret;

  @Transactional
  @ActiveUserRequired
  public UserVerificationTokenIssueResponse issueToken(Long userId) {
    return issueTokenInternal(userId, false);
  }

  @Transactional
  @ActiveUserRequired
  public UserVerificationTokenIssueResponse reissueToken(Long userId) {
    return issueTokenInternal(userId, true);
  }

  @Transactional(readOnly = true)
  @ActiveUserRequired
  public UserVerificationTokenResponse getMyToken(Long userId) {
    UserVerificationToken token = userVerificationTokenRepository
      .findTopByUserIdOrderByIssuedAtDescIdDesc(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_VERIFICATION_TOKEN_NOT_FOUND));

    return toTokenResponse(token);
  }

  @Transactional(readOnly = true)
  @ActiveUserRequired
  public UserVerificationInfoResponse getMyVerificationInfo(Long userId) {
    ensureUserExists(userId);
    return UserVerificationInfoResponse.of(userId, userVerificationRepository.findByUserId(userId)
      .orElse(null));
  }

  @Transactional(readOnly = true)
  @ActiveUserRequired
  public UserVerificationHistoryListResponse getMyVerificationHistories(Long userId, String sort, Integer limit) {
    ensureUserExists(userId);

    int normalizedLimit = normalizeLimit(limit);
    String normalizedSort = normalizeSort(sort);

    List<UserVerificationHistoryResponse> items = userVerificationHistoryRepository.findTop100ByUserIdOrderByVerifiedAtDesc(
        userId)
      .stream()
      .map(UserVerificationHistoryResponse::of)
      .toList();

    if ("oldest".equals(normalizedSort)) {
      items = items.stream()
        .collect(java.util.stream.Collectors.toList());
      Collections.reverse(items);
    }

    if (items.size() > normalizedLimit) {
      items = items.subList(0, normalizedLimit);
    }

    return new UserVerificationHistoryListResponse(
      normalizedSort,
      normalizedLimit,
      items.size(),
      items
    );
  }

  @Transactional(readOnly = true)
  public UserVerificationInfoResponse getUserVerificationInfo(Long userId) {
    ensureUserExists(userId);
    return UserVerificationInfoResponse.of(userId, userVerificationRepository.findByUserId(userId)
      .orElse(null));
  }

  @Transactional(readOnly = true)
  public UserVerificationPublicSummaryResponse getUserVerificationPublicSummary(Long userId, Integer limit) {
    ensureUserExists(userId);

    int normalizedLimit = normalizeLimit(limit);

    List<UserVerificationHistoryResponse> histories = userVerificationHistoryRepository
      .findTop100ByUserIdOrderByVerifiedAtDesc(userId)
      .stream()
      .limit(normalizedLimit)
      .map(UserVerificationHistoryResponse::of)
      .toList();

    return UserVerificationPublicSummaryResponse.of(
      userId,
      userVerificationRepository.findByUserId(userId)
        .orElse(null),
      histories
    );
  }

  @Transactional(propagation = Propagation.NEVER)
  public void verifyFromKafkaPayload(String payload) {
    UserVerificationVerifyMessage event;
    try {
      event = objectMapper.readValue(payload, UserVerificationVerifyMessage.class);
    } catch (JsonProcessingException e) {
      log.warn("Invalid kafka payload for verification");
      recordUnassignedFailure(null, null, VerificationFailureReason.INVALID_MESSAGE);
      return;
    }
    if (event == null || event.verificationValue() == null || event.verificationValue().isBlank()) {
      recordUnassignedFailure(event == null ? null : event.serverName(),
        event == null ? null : event.characterName(), VerificationFailureReason.INVALID_MESSAGE);
      return;
    }
    String value = normalizeTokenValue(event.verificationValue());
    try {
      for (int attempt = 0; attempt < 3; attempt++) {
        Long claimantId = userVerificationTokenRepository.findUserIdByTokenValue(value).orElse(null);
        if (claimantId == null) {
          recordUnassignedFailure(event.serverName(), event.characterName(), VerificationFailureReason.TOKEN_NOT_FOUND);
          return;
        }
        Long ownerId = userVerificationRepository.findProfileOwnerId(event.serverName(), event.characterName())
          .orElse(null);
        UserVerificationAttemptService.AttemptState state = new UserVerificationAttemptService.AttemptState();
        try {
          verificationAttemptService.execute(event, value, claimantId, ownerId, state);
          return;
        } catch (RuntimeException e) {
          if (state.publicationStarted || state.methodCompleted || !isRetryableVerificationFailure(e)) throw e;
          if (attempt == 2) throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_INVALID);
        }
      }
    } catch (CustomException e) {
      log.error("User verification processing failed. code={}", e.getCode().getCode());
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error while processing user verification. exceptionType={}", e.getClass().getSimpleName());
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_INVALID);
    }
  }

  private boolean isRetryableVerificationFailure(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof UserVerificationAttemptService.OwnerChangedException) return true;
      if (cause instanceof java.sql.SQLException sql) {
        if (sql.getErrorCode() == 1213 || sql.getErrorCode() == 1205) return true;
        if (sql.getErrorCode() == 1062 && sql.getMessage() != null) {
          String message = sql.getMessage().toLowerCase(java.util.Locale.ROOT);
          return message.contains("uk_server_nickname") || message.contains("uk_unverified_nickname");
        }
      }
    }
    return false;
  }

  private void recordUnassignedFailure(String server, String character, VerificationFailureReason reason) {
    userVerificationHistoryService.saveFailureHistory(null, null, server, character, reason);
    logVerificationResult(false, reason.name(), null);
  }

  private UserVerificationTokenIssueResponse issueTokenInternal(Long userId, boolean reissue) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));
    entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);

    LocalDateTime now = LocalDateTime.now();

    UserVerification verification = userVerificationRepository.findByUserIdForUpdate(userId)
      .orElse(null);
    if (verification != null) entityManager.refresh(verification, LockModeType.PESSIMISTIC_WRITE);
    if (verification != null && verification.hasRecentSuccess(now.minusDays(verificationCooldownDays))) {
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_COOLDOWN_ACTIVE);
    }

    List<UserVerificationToken> activeTokens = userVerificationTokenRepository.findActiveForUpdate(userId, now);

    if (!reissue && !activeTokens.isEmpty()) {
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_TOKEN_ALREADY_EXISTS);
    }

    if (reissue) activeTokens.forEach(token -> token.revoke(now));

    String verificationValue = generateUniqueVerificationValue(userId, now);
    LocalDateTime expiresAt = now.plusMinutes(verificationValidityMinutes);

    userVerificationTokenRepository.save(
      UserVerificationToken.builder()
        .user(user)
        .issuedAt(now)
        .expiresAt(expiresAt)
        .revoked(false)
        .verified(false)
        .tokenValue(verificationValue)
        .build()
    );

    return new UserVerificationTokenIssueResponse(verificationPrefix + verificationValue, expiresAt);
  }

  private UserVerificationTokenResponse toTokenResponse(UserVerificationToken token) {
    LocalDateTime now = LocalDateTime.now();
    long expiresInSeconds = Math.max(0, Duration.between(now, token.getExpiresAt())
      .getSeconds());
    VerificationTokenStatus status = resolveTokenStatus(token, now);

    return new UserVerificationTokenResponse(
      token.getId(),
      verificationPrefix + token.getTokenValue(),
      status.name(),
      token.getIssuedAt(),
      token.getExpiresAt(),
      expiresInSeconds,
      token.isRevoked(),
      token.isVerified()
    );
  }

  private VerificationTokenStatus resolveTokenStatus(UserVerificationToken token, LocalDateTime now) {
    if (token.isVerified()) {
      return VerificationTokenStatus.VERIFIED;
    }
    if (token.isRevoked()) {
      return VerificationTokenStatus.REVOKED;
    }
    if (token.isExpired(now)) {
      return VerificationTokenStatus.EXPIRED;
    }
    return VerificationTokenStatus.ACTIVE;
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null) {
      return 20;
    }
    if (limit < 1 || limit > 100) {
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_HISTORY_LIMIT_INVALID);
    }
    return limit;
  }

  private String normalizeSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return "latest";
    }
    return "oldest".equalsIgnoreCase(sort) ? "oldest" : "latest";
  }

  private void logVerificationResult(
    boolean success,
    String result,
    Long userId
  ) {
    log.info(
      "User verification {} via kafka. result={}, userId={}",
      success ? "success" : "failure",
      result,
      userId
    );
  }

  private String normalizeTokenValue(String tokenValue) {
    if (tokenValue == null) {
      return null;
    }
    return tokenValue.startsWith(verificationPrefix)
      ? tokenValue.substring(verificationPrefix.length())
      : tokenValue;
  }

  private void ensureUserExists(Long userId) {
    if (!userRepository.existsById(userId)) {
      throw new CustomException(GlobalExceptionCode.USER_NOT_EXISTS);
    }
  }

  private String generateUniqueVerificationValue(Long userId, LocalDateTime issuedAt) {
    long issuedAtMillis = issuedAt.toInstant(ZoneOffset.UTC)
      .toEpochMilli();

    for (int retry = 0; retry < MAX_GENERATION_RETRY; retry++) {
      String candidate = buildHashedVerificationValue(userId, issuedAtMillis);
      if (!userVerificationTokenRepository.existsByTokenValue(candidate)) {
        return candidate;
      }
    }

    throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_GENERATE_FAILED);
  }

  private String buildHashedVerificationValue(Long userId, long issuedAtMillis) {
    byte[] nonce = new byte[NONCE_BYTES_LENGTH];
    secureRandom.nextBytes(nonce);

    String payload = userId + ":" + issuedAtMillis + ":" + toHex(nonce);
    String digest = hmacSha256Hex(payload, verificationHashSecret);

    if (verificationLength > digest.length()) {
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_GENERATE_FAILED);
    }

    return digest.substring(0, verificationLength);
  }

  private String hmacSha256Hex(String message, String secret) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      SecretKeySpec secretKeySpec =
        new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
      mac.init(secretKeySpec);

      return toHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_GENERATE_FAILED);
    }
  }

  private String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      builder.append(String.format("%02X", b));
    }
    return builder.toString();
  }
}
