package until.the.eternity.das.verification.kafka;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class UserVerificationContractTest {
    @Test
    void readsBatchProducerFixtureAndIsoInstant() throws Exception {
        var mapper = Jackson2ObjectMapperBuilder.json().build();
        String payload = Files.readString(Path.of("src/test/resources/contracts/user-verification-verify.json"));
        var event = mapper.readValue(payload, UserVerificationVerifyMessage.class);
        assertEquals("contract-character", event.characterName());
        assertEquals("류트", event.serverName());
        assertEquals("fixture-only-token", event.verificationValue());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), event.dateSend());
        var isoPayload = mapper.readTree(payload);
        ((com.fasterxml.jackson.databind.node.ObjectNode) isoPayload).put("dateSend", "2026-01-01T00:00:00Z");
        assertEquals(event, mapper.treeToValue(isoPayload, UserVerificationVerifyMessage.class));
    }
}
