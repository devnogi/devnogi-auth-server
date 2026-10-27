package until.the.eternity.das.verification.dto.response;

import java.time.LocalDateTime;

public record UserVerificationTokenIssueResponse(
  String verificationCode,
  LocalDateTime expiresAt
) {
}
