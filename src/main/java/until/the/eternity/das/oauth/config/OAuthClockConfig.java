package until.the.eternity.das.oauth.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
public class OAuthClockConfig {
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(
            name = "app.experience.operator-enabled",
            havingValue = "false",
            matchIfMissing = true)
    static class SchedulingConfiguration {}

    @Bean
    public Clock oauthClock() {
        return Clock.systemUTC();
    }
}
