package until.the.eternity.das.verification.dto.response;

import java.time.LocalDateTime;
import until.the.eternity.das.verification.entity.UserVerificationHistory;

public record UserVerificationHistoryResponse(
  Long historyId,
  String serverName,
  String characterName,
  LocalDateTime verifiedAt,
  boolean verificationSuccess,
  String resultCode,
  String resultMessage,
  String failureReason,
  Long tokenId
) {

  public static UserVerificationHistoryResponse of(UserVerificationHistory history) {
    String failureReason = history.getFailureReason() == null ? null : history.getFailureReason().name();

    return new UserVerificationHistoryResponse(
      history.getId(),
      history.getServerName(),
      history.getCharacterName(),
      history.getVerifiedAt(),
      history.isVerificationSuccess(),
      history.isVerificationSuccess() ? "SUCCESS" : "FAIL",
      history.isVerificationSuccess() ? "인증 성공" : "인증 실패",
      failureReason,
      history.getToken() == null ? null : history.getToken().getId()
    );
  }
}
