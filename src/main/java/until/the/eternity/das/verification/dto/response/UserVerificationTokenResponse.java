package until.the.eternity.das.verification.dto.response;

import java.time.LocalDateTime;

public record UserVerificationTokenResponse(
  Long tokenId,
  String verificationCode,
  String tokenStatus,
  LocalDateTime issuedAt,
  LocalDateTime expiresAt,
  Long expiresInSeconds,
  boolean revoked,
  boolean verified
) {
}
