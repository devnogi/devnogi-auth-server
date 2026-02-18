package until.the.eternity.das.verification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.common.aop.ActiveUserRequired;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.verification.dto.response.UserVerificationHistoryResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationHistoryListResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationInfoResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationPublicSummaryResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationTokenIssueResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationTokenResponse;
import until.the.eternity.das.verification.entity.UserVerification;
import until.the.eternity.das.verification.entity.UserVerificationHistory;
import until.the.eternity.das.verification.entity.UserVerificationHistoryRepository;
import until.the.eternity.das.verification.entity.UserVerificationRepository;
import until.the.eternity.das.verification.entity.UserVerificationToken;
import until.the.eternity.das.verification.entity.UserVerificationTokenRepository;
import until.the.eternity.das.verification.entity.enums.VerificationFailureReason;
import until.the.eternity.das.verification.entity.enums.VerificationTokenStatus;
import until.the.eternity.das.verification.kafka.UserVerificationVerifyMessage;

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
      .findTopByUserIdOrderByIssuedAtDesc(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_VERIFICATION_TOKEN_NOT_FOUND));

    return toTokenResponse(token);
  }

  @Transactional(readOnly = true)
  @ActiveUserRequired
  public UserVerificationInfoResponse getMyVerificationInfo(Long userId) {
    ensureUserExists(userId);
    return UserVerificationInfoResponse.of(userId, userVerificationRepository.findByUserId(userId).orElse(null));
  }

  @Transactional(readOnly = true)
  @ActiveUserRequired
  public UserVerificationHistoryListResponse getMyVerificationHistories(Long userId, String sort, Integer limit) {
    ensureUserExists(userId);

    int normalizedLimit = normalizeLimit(limit);
    String normalizedSort = normalizeSort(sort);

    List<UserVerificationHistoryResponse> items = userVerificationHistoryRepository.findTop100ByUserIdOrderByVerifiedAtDesc(userId)
      .stream()
      .map(UserVerificationHistoryResponse::of)
      .toList();

    if ("oldest".equals(normalizedSort)) {
      items = items.stream().collect(java.util.stream.Collectors.toList());
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
    return UserVerificationInfoResponse.of(userId, userVerificationRepository.findByUserId(userId).orElse(null));
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
      userVerificationRepository.findByUserId(userId).orElse(null),
      histories
    );
  }

  @Transactional
  public void verifyFromKafkaPayload(String payload) {
    try {
      UserVerificationVerifyMessage event = parseMessage(payload);
      if (event == null) {
        return;
      }

      LocalDateTime now = LocalDateTime.now();

      if (event.verificationValue() == null || event.verificationValue().isBlank()) {
        saveFailureHistory(null, null, event.verificationValue(), event.serverName(), event.characterName(), now,
          VerificationFailureReason.INVALID_MESSAGE);
        return;
      }

      String normalizedTokenValue = normalizeTokenValue(event.verificationValue());
      UserVerificationToken token = userVerificationTokenRepository.findByTokenValue(normalizedTokenValue).orElse(null);

      if (token == null) {
        saveFailureHistory(null, null, normalizedTokenValue, event.serverName(), event.characterName(), now,
          VerificationFailureReason.TOKEN_NOT_FOUND);
        return;
      }

      User user = token.getUser();

      if (token.isRevoked()) {
        saveFailureHistory(user, token, normalizedTokenValue, event.serverName(), event.characterName(), now,
          VerificationFailureReason.TOKEN_REVOKED);
        return;
      }

      if (token.isExpired(now)) {
        saveFailureHistory(user, token, normalizedTokenValue, event.serverName(), event.characterName(), now,
          VerificationFailureReason.TOKEN_EXPIRED);
        return;
      }

      if (token.isVerified()) {
        saveFailureHistory(user, token, normalizedTokenValue, event.serverName(), event.characterName(), now,
          VerificationFailureReason.TOKEN_ALREADY_VERIFIED);
        return;
      }

      if (event.serverName() == null || event.serverName().isBlank()
        || event.characterName() == null || event.characterName().isBlank()) {
        saveFailureHistory(user, token, normalizedTokenValue, event.serverName(), event.characterName(), now,
          VerificationFailureReason.INVALID_MESSAGE);
        return;
      }

      invalidatePreviousOwner(event.serverName(), event.characterName(), user.getId());

      UserVerification verification = userVerificationRepository.findByUserId(user.getId())
        .orElseGet(() -> UserVerification.builder().user(user).verificationCount(0).verified(false).build());

      verification.markVerified(event.serverName(), event.characterName(), now, token.getId());
      token.markVerified(now);

      user.updateServerName(event.serverName());
      user.updateVerificationStatus(true, now);

      userVerificationRepository.save(verification);

      userVerificationHistoryRepository.save(
        UserVerificationHistory.builder()
          .user(user)
          .serverName(event.serverName())
          .characterName(event.characterName())
          .verifiedAt(now)
          .verificationSuccess(true)
          .token(token)
          .build()
      );

      logVerificationResult(true, "VERIFIED", user.getId(), normalizedTokenValue, event.serverName(), event.characterName());
    } catch (CustomException e) {
      log.error("User verification processing failed. payload={}", payload, e);
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error while processing user verification payload. payload={}", payload, e);
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_INVALID);
    }
  }

  private UserVerificationTokenIssueResponse issueTokenInternal(Long userId, boolean reissue) {
    User user = userRepository.findById(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

    LocalDateTime now = LocalDateTime.now();

    UserVerification verification = userVerificationRepository.findByUserId(userId).orElse(null);
    if (verification != null && verification.hasRecentSuccess(now.minusDays(verificationCooldownDays))) {
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_COOLDOWN_ACTIVE);
    }

    UserVerificationToken activeToken = userVerificationTokenRepository
      .findTopByUserIdAndRevokedFalseAndVerifiedFalseAndExpiresAtAfterOrderByIssuedAtDesc(userId, now)
      .orElse(null);

    if (!reissue && activeToken != null) {
      throw new CustomException(GlobalExceptionCode.USER_VERIFICATION_TOKEN_ALREADY_EXISTS);
    }

    if (reissue && activeToken != null) {
      activeToken.revoke(now);
    }

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

  private void invalidatePreviousOwner(String serverName, String characterName, Long currentUserId) {
    UserVerification existingOwner = userVerificationRepository
      .findByServerNameAndCharacterNameAndVerifiedTrue(serverName, characterName)
      .orElse(null);

    if (existingOwner == null || existingOwner.getUser().getId().equals(currentUserId)) {
      return;
    }

    existingOwner.invalidate();
    existingOwner.getUser().updateVerificationStatus(false, null);
  }

  private UserVerificationVerifyMessage parseMessage(String payload) {
    try {
      return objectMapper.readValue(payload, UserVerificationVerifyMessage.class);
    } catch (JsonProcessingException e) {
      log.warn("Invalid kafka payload for verification. payload={}", payload);
      saveFailureHistory(null, null, null, null, null, LocalDateTime.now(), VerificationFailureReason.INVALID_MESSAGE);
      return null;
    }
  }

  private void saveFailureHistory(
    User user,
    UserVerificationToken token,
    String providedTokenValue,
    String serverName,
    String characterName,
    LocalDateTime now,
    VerificationFailureReason reason
  ) {
    userVerificationHistoryRepository.save(
      UserVerificationHistory.builder()
        .user(user)
        .serverName(serverName)
        .characterName(characterName)
        .verifiedAt(now)
        .verificationSuccess(false)
        .failureReason(reason)
        .token(token)
        .build()
    );

    String tokenValueForLog = providedTokenValue != null
      ? providedTokenValue
      : token == null ? null : token.getTokenValue();

    logVerificationResult(
      false,
      reason.name(),
      user == null ? null : user.getId(),
      tokenValueForLog,
      serverName,
      characterName
    );
  }

  private UserVerificationTokenResponse toTokenResponse(UserVerificationToken token) {
    LocalDateTime now = LocalDateTime.now();
    long expiresInSeconds = Math.max(0, Duration.between(now, token.getExpiresAt()).getSeconds());
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
    Long userId,
    String tokenValue,
    String serverName,
    String characterName
  ) {
    log.info(
      "User verification {} via kafka. result={}, userId={}, tokenValue={}, serverName={}, characterName={}",
      success ? "success" : "failure",
      result,
      userId,
      tokenValue,
      serverName,
      characterName
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
    long issuedAtMillis = issuedAt.toInstant(ZoneOffset.UTC).toEpochMilli();

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
