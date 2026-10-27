package until.the.eternity.das.verification.dto.response;

import java.time.LocalDateTime;
import until.the.eternity.das.verification.entity.UserVerification;

public record UserVerificationInfoResponse(
  Long userId,
  boolean verified,
  String verificationState,
  String serverName,
  String characterName,
  String verificationIdentity,
  LocalDateTime lastVerifiedAt,
  int verificationCount,
  Long latestTokenId
) {

  public static UserVerificationInfoResponse of(Long userId, UserVerification verification) {
    if (verification == null) {
      return new UserVerificationInfoResponse(userId, false, "UNVERIFIED", null, null, null, null, 0, null);
    }

    String identity = null;
    if (verification.getServerName() != null && verification.getCharacterName() != null) {
      identity = verification.getServerName() + ":" + verification.getCharacterName();
    }

    return new UserVerificationInfoResponse(
      userId,
      verification.isVerified(),
      verification.isVerified() ? "VERIFIED" : "UNVERIFIED",
      verification.getServerName(),
      verification.getCharacterName(),
      identity,
      verification.getLastVerifiedAt(),
      verification.getVerificationCount(),
      verification.getLatestTokenId()
    );
  }
}
