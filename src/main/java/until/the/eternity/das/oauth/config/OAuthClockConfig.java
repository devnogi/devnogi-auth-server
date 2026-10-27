package until.the.eternity.das.oauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class OAuthClockConfig {
  @Bean
  public Clock oauthClock() {
    return Clock.systemUTC();
  }
}
