package until.the.eternity.das.verification.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.common.application.KafkaProducerService;
import until.the.eternity.das.user.application.UserService;
import until.the.eternity.das.user.dto.response.UserInfoUpdateEvent;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.verification.entity.UserVerification;
import until.the.eternity.das.verification.entity.UserVerificationHistory;
import until.the.eternity.das.verification.entity.UserVerificationHistoryRepository;
import until.the.eternity.das.verification.entity.UserVerificationRepository;
import until.the.eternity.das.verification.entity.UserVerificationToken;
import until.the.eternity.das.verification.entity.UserVerificationTokenRepository;
import until.the.eternity.das.verification.entity.enums.VerificationFailureReason;
import until.the.eternity.das.verification.kafka.UserVerificationVerifyMessage;

/** One independent transaction; caller may retry only before publication begins. */
@Service
@RequiredArgsConstructor
public class UserVerificationAttemptService {
  private static final Logger LOG = LoggerFactory.getLogger(UserVerificationAttemptService.class);
  private final EntityManager entityManager;
  private final UserVerificationTokenRepository tokens;
  private final UserVerificationRepository verifications;
  private final UserVerificationHistoryRepository histories;
  private final UserService userService;
  private final KafkaProducerService producer;

  static final class AttemptState {
    boolean publicationStarted;
    boolean methodCompleted;
  }

  static final class OwnerChangedException extends RuntimeException { }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void execute(UserVerificationVerifyMessage event, String value, Long claimantId,
                      Long observedOwnerId, AttemptState state) {
    List<Long> ids = new ArrayList<>();
    ids.add(claimantId);
    if (observedOwnerId != null && !observedOwnerId.equals(claimantId)) ids.add(observedOwnerId);
    ids.sort(Long::compareTo);
    for (Long id : ids) {
      if (entityManager.find(User.class, id, LockModeType.PESSIMISTIC_WRITE) == null) {
        throw new OwnerChangedException();
      }
    }

    Long ownerId = verifications.findProfileOwnerId(event.serverName(), event.characterName()).orElse(null);
    if (ownerId != null && !ids.contains(ownerId)) throw new OwnerChangedException();

    User user = entityManager.find(User.class, claimantId);
    UserVerificationToken token = tokens.findByTokenValueForUpdate(value).orElse(null);
    if (token == null || !Objects.equals(token.getUser().getId(), claimantId)) throw new OwnerChangedException();
    entityManager.refresh(token, LockModeType.PESSIMISTIC_WRITE);
    LocalDateTime now = LocalDateTime.now();
    List<UserInfoUpdateEvent> events = new ArrayList<>();

    if (token.isRevoked()) {
      failure(user, token, event, now, VerificationFailureReason.TOKEN_REVOKED);
    } else if (token.isExpired(now)) {
      failure(user, token, event, now, VerificationFailureReason.TOKEN_EXPIRED);
    } else if (token.isVerified()) {
      failure(user, token, event, now, VerificationFailureReason.TOKEN_ALREADY_VERIFIED);
    } else if (event.serverName() == null || event.serverName().isBlank()
        || event.characterName() == null || event.characterName().isBlank()
        || event.characterName().length() > 50) {
      failure(user, token, event, now, VerificationFailureReason.INVALID_MESSAGE);
    } else {
      if (ownerId != null && !ownerId.equals(claimantId)) {
        User oldOwner = entityManager.find(User.class, ownerId);
        UserVerification oldSummary = verifications.findByUserIdForUpdate(ownerId)
          .orElseThrow(IllegalStateException::new);
        entityManager.refresh(oldSummary, LockModeType.PESSIMISTIC_WRITE);
        if (!oldSummary.isVerified() || !oldSummary.matchesIdentity(event.serverName(), event.characterName())) {
          throw new IllegalStateException("Profile and verification owner differ");
        }
        oldSummary.invalidate();
        oldOwner.updateVerificationStatus(false, null);
        oldOwner.updateGameProfile(userService.generateRandomNickname(), null);
        entityManager.flush();
        events.add(UserInfoUpdateEvent.of(oldOwner));
      }

      UserVerification summary = verifications.findByUserIdForUpdate(claimantId).orElse(null);
      if (summary == null) {
        summary = UserVerification.builder().user(user).verificationCount(0).verified(false).build();
      } else {
        entityManager.refresh(summary, LockModeType.PESSIMISTIC_WRITE);
      }
      for (UserVerificationToken active : tokens.findActiveForUpdate(claimantId, now)) {
        if (!active.getId().equals(token.getId())) active.revoke(now);
      }
      summary.markVerified(event.serverName(), event.characterName(), now, token.getId());
      token.markVerified(now);
      user.updateGameProfile(event.characterName(), event.serverName());
      user.updateVerificationStatus(true, now);
      verifications.save(summary);
      histories.save(UserVerificationHistory.builder().user(user).serverName(event.serverName())
        .characterName(event.characterName()).verifiedAt(now).verificationSuccess(true).token(token).build());
      events.add(UserInfoUpdateEvent.of(user));
      LOG.info("User verification success via kafka. result=VERIFIED, userId={}", claimantId);
    }

    entityManager.flush();
    for (UserInfoUpdateEvent update : events) {
      state.publicationStarted = true;
      producer.sendUserInfoUpdateEvent(update);
    }
    state.methodCompleted = true;
  }

  private void failure(User user, UserVerificationToken token, UserVerificationVerifyMessage event,
                       LocalDateTime now, VerificationFailureReason reason) {
    histories.save(UserVerificationHistory.builder().user(user).token(token).serverName(event.serverName())
      .characterName(event.characterName()).failureReason(reason).verifiedAt(now).verificationSuccess(false).build());
    LOG.info("User verification failure via kafka. result={}, userId={}", reason.name(), user.getId());
  }
}
