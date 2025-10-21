package until.the.eternity.das.common.application;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import until.the.eternity.das.common.constant.UserUpdateConstant;
import until.the.eternity.das.user.dto.response.UserInfoUpdateEvent;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

  private final KafkaTemplate<String, UserInfoUpdateEvent> kafkaTemplate;

  public void sendUserInfoUpdateEvent(UserInfoUpdateEvent event) {
    kafkaTemplate.send(UserUpdateConstant.INFO, event);
  }

}
