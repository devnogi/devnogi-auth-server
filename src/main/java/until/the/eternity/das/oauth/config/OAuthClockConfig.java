package until.the.eternity.das.oauth.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class OAuthClockConfig {
    @Bean
    public Clock oauthClock() {
        return Clock.systemUTC();
    }
}
