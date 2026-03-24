package until.the.eternity.das.verification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.verification.entity.UserVerificationHistory;
import until.the.eternity.das.verification.entity.UserVerificationHistoryRepository;
import until.the.eternity.das.verification.entity.UserVerificationToken;
import until.the.eternity.das.verification.entity.enums.VerificationFailureReason;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserVerificationHistoryService {

  private final UserVerificationHistoryRepository userVerificationHistoryRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveFailureHistory(
    User user,
    UserVerificationToken token,
    String serverName,
    String characterName,
    VerificationFailureReason reason) {

    UserVerificationHistory history = UserVerificationHistory.builder()
      .user(user)
      .serverName(serverName)
      .characterName(characterName)
      .token(token)
      .failureReason(reason)
      .verifiedAt(LocalDateTime.now())
      .verificationSuccess(false)
      .build();

    userVerificationHistoryRepository.save(history);


  }
}
