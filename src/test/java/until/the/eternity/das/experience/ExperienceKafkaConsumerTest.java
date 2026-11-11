package until.the.eternity.das.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExperienceKafkaConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ExperienceService service = mock(ExperienceService.class);
    private final ExperienceKafkaConsumer consumer = new ExperienceKafkaConsumer(mapper, service);

    @Test
    void producerFixtureDeserializesAndPropagatesTransientFailure() throws Exception {
        String payload =
                Files.readString(Path.of("src/test/resources/contracts/user-activity.json"));
        consumer.consume(payload);
        ArgumentCaptor<ActivityEvent> event = ArgumentCaptor.forClass(ActivityEvent.class);
        verify(service).apply(event.capture());
        event.getValue().validate();
        assertThat(event.getValue().activityKey()).isEqualTo("COMMENT:101:LIKE:20");
        doThrow(new IllegalStateException("database unavailable")).when(service).apply(any());
        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingOrNullRequiredFieldsNeverReachLedger() throws Exception {
        String payload =
                Files.readString(Path.of("src/test/resources/contracts/user-activity.json"));
        for (String field :
                List.of(
                        "active",
                        "sourceRevision",
                        "actorUserId",
                        "occurredAt",
                        "eventId",
                        "schemaVersion")) {
            ObjectNode missing = (ObjectNode) mapper.readTree(payload);
            missing.remove(field);
            assertThatThrownBy(() -> consumer.consume(missing.toString()))
                    .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
            ObjectNode nullValue = (ObjectNode) mapper.readTree(payload);
            nullValue.putNull(field);
            assertThatThrownBy(() -> consumer.consume(nullValue.toString()))
                    .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }
        verifyNoInteractions(service);
    }
}
