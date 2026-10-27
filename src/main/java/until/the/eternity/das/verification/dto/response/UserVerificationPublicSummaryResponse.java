package until.the.eternity.das.verification.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import until.the.eternity.das.verification.entity.UserVerification;

public record UserVerificationPublicSummaryResponse(
  Long userId,
  boolean verified,
  String serverName,
  String characterName,
  LocalDateTime lastVerifiedAt,
  int verificationCount,
  List<UserVerificationHistoryResponse> histories
) {

  public static UserVerificationPublicSummaryResponse of(
    Long userId,
    UserVerification verification,
    List<UserVerificationHistoryResponse> histories
  ) {
    if (verification == null) {
      return new UserVerificationPublicSummaryResponse(
        userId,
        false,
        null,
        null,
        null,
        0,
        histories
      );
    }

    return new UserVerificationPublicSummaryResponse(
      userId,
      verification.isVerified(),
      verification.getServerName(),
      verification.getCharacterName(),
      verification.getLastVerifiedAt(),
      verification.getVerificationCount(),
      histories
    );
  }
}
