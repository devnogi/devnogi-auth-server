package until.the.eternity.das.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserSearchObservabilityContractTest {

    @Test
    void fallbackHealthAndFailureMetricsRemainDocumentedAndExposed() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application-sample.yml"));
        String security =
                Files.readString(
                        Path.of(
                                "src/main/java/until/the/eternity/das/common/config/SecurityConfig.java"));
        String documentation = Files.readString(Path.of("docs/development.md"));

        assertThat(application)
                .contains("include: [ \"health\", \"metrics\" ]")
                .contains("enabled: ${MANAGEMENT_HEALTH_REDIS_ENABLED:false}");
        assertThat(security)
                .contains("requestMatchers(\"/actuator/health\")")
                .contains(
                        "requestMatchers(\"/actuator/metrics\", \"/actuator/metrics/**\")",
                        ".hasAnyRole(\"ADMIN\", \"SUPER_ADMIN\")");
        assertThat(documentation)
                .contains(
                        "memonogi.search.cache.read.failures",
                        "memonogi.search.cache.write.failures",
                        "redis:6389");
    }
}
