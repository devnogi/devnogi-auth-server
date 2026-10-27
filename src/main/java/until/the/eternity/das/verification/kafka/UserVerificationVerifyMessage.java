package until.the.eternity.das.verification.kafka;

import java.time.Instant;

public record UserVerificationVerifyMessage(
  String characterName,
  String serverName,
  String verificationValue,
  String message,
  Instant dateSend
) {
}
