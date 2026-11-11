package until.the.eternity.das.experience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExperienceKafkaConsumer {
    private final ObjectMapper mapper;
    private final ExperienceService experience;

    @KafkaListener(
            topics = "${app.experience.topic:USER_ACTIVITY_EVENT}",
            groupId = "${app.experience.group-id:devnogi-auth-experience-v1}",
            containerFactory = "experienceKafkaListenerContainerFactory",
            autoStartup = "${app.experience.consumer-enabled:false}")
    public void consume(String payload) throws JsonProcessingException {
        ActivityEvent event =
                mapper.readerFor(ActivityEvent.class)
                        .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                        .with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                        .readValue(payload);
        experience.apply(event);
    }
}
