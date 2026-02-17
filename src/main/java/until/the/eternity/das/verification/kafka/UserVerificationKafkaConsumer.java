package until.the.eternity.das.verification.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import until.the.eternity.das.verification.application.UserVerificationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserVerificationKafkaConsumer {

  private final UserVerificationService userVerificationService;

  @KafkaListener(
    topics = "${app.kafka.topics.user-verification-verify:USER_VERIFICATION_VERIFY_EVENT}",
    groupId = "${spring.kafka.consumer.group-id:devnogi-auth-user-verification-consumer}"
  )
  public void consumeVerificationMessage(String payload) {
    log.debug("Received user verification kafka message. payload={}", payload);
    userVerificationService.verifyFromKafkaPayload(payload);
  }
}
