package until.the.eternity.das.common.application;

import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import until.the.eternity.das.common.constant.UserUpdateConstant;
import until.the.eternity.das.user.dto.response.UserInfoUpdateEvent;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, UserInfoUpdateEvent> kafkaTemplate;

    public CompletableFuture<SendResult<String, UserInfoUpdateEvent>> sendUserInfoUpdateEvent(
            String userId, UserInfoUpdateEvent event) {
        return kafkaTemplate.send(UserUpdateConstant.INFO, userId, event);
    }
}
