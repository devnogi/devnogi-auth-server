package until.the.eternity.das.user.dto.response;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;
import until.the.eternity.das.auth.application.AuthConverter;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.oauth.dto.OauthUserDTO;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.user.entity.User;

class UserInfoUpdateContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AuthConverter converter = new AuthConverter();
    private final Role role = Role.builder().id(1L).name(Name.USER).build();

    @Test
    void registeredEmailUserPublishesNullServerAndProfile() throws Exception {
        var request =
                new SignUpRequest(
                        "contract@example.invalid", "synthetic-password", "contract-user", null);
        var user = converter.fromUserSignUpRequestToUser(request, "synthetic-hash", role, null);
        assertRegisteredUserMatchesFixture(user);
    }

    @Test
    void registeredSocialUserPublishesNullServerAndProfile() throws Exception {
        var oauth = new OauthUserDTO("synthetic-provider-id", "contract@example.invalid", "google");
        var user = converter.fromOauthUserDTOToUser(oauth, "contract-user", role, null);
        assertRegisteredUserMatchesFixture(user);
    }

    @Test
    void verifiedUserPublishesServerAndOptionalProfile() throws Exception {
        var event =
                new UserInfoUpdateEvent(
                        42L, "contract-user", "https://example.invalid/profile.png", "류트");
        JsonNode payload = assertPayloadMatchesSchema(event);
        assertEquals("류트", payload.path("serverName").asText());
        assertEquals(event.profileImageUrl(), payload.path("profileImageUrl").asText());
    }

    private void assertRegisteredUserMatchesFixture(User user) throws Exception {
        // Simulate the identifier assigned by JPA before the existing publication call.
        ReflectionTestUtils.setField(user, "id", 42L);
        assertFalse(user.isVerified());
        assertNull(user.getServerName());
        assertNull(user.getProfileImageUrl());
        JsonNode payload = assertPayloadMatchesSchema(UserInfoUpdateEvent.of(user));
        JsonNode fixture =
                mapper.readTree(
                        Files.readString(Path.of("contracts/user-info-update.example.json")));
        assertEquals(fixture, payload);
    }

    private JsonNode assertPayloadMatchesSchema(UserInfoUpdateEvent event) throws Exception {
        try (var serializer = new JsonSerializer<UserInfoUpdateEvent>()) {
            JsonNode payload =
                    mapper.readTree(serializer.serialize("USER_INFO_UPDATE_EVENT", event));
            JsonNode schema =
                    mapper.readTree(
                            Files.readString(Path.of("contracts/user-info-update.schema.json")));
            Set<String> declared = new HashSet<>();
            schema.path("properties").fieldNames().forEachRemaining(declared::add);
            Set<String> actual = new HashSet<>();
            payload.fieldNames().forEachRemaining(actual::add);
            assertEquals(declared, actual);
            for (JsonNode field : schema.path("required")) {
                assertTrue(payload.has(field.asText()), field.asText());
            }
            for (String field : declared) {
                JsonNode value = payload.get(field);
                JsonNode rule = schema.path("properties").path(field);
                String type =
                        value.isNull()
                                ? "null"
                                : value.isIntegralNumber()
                                        ? "integer"
                                        : value.isTextual() ? "string" : "unsupported";
                JsonNode allowed = rule.path("type");
                boolean valid = allowed.isTextual() && allowed.asText().equals(type);
                for (JsonNode candidate : allowed) {
                    valid |= candidate.asText().equals(type);
                }
                assertTrue(valid, field + " has unexpected wire type " + type);
                if (value.isTextual() && rule.has("minLength")) {
                    assertTrue(value.asText().length() >= rule.path("minLength").asInt(), field);
                }
            }
            return payload;
        }
    }
}
