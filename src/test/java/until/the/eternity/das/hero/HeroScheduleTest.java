package until.the.eternity.das.hero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import until.the.eternity.das.experience.ExperienceWalletService;

class HeroScheduleTest {
    @Test
    void disabledConfigurationDoesNotAdvertiseScheduledStart() {
        HeroAuctionService service = service(false, "2026-11-16", "2026-11-12T00:00:00Z");
        assertThat(service.enabled()).isFalse();
        assertThat(service.current().scheduledStartAt()).isNull();
        assertThat(service.all().scheduledStartAt()).isNull();
    }

    @Test
    void scheduledStartIsKoreanMondayMidnightAndDisappearsAtOpening() {
        HeroAuctionService before = service(true, "2026-11-16", "2026-11-15T14:59:59Z");
        assertThat(before.current().enabled()).isFalse();
        assertThat(before.current().scheduledStartAt())
                .isEqualTo(Instant.parse("2026-11-15T15:00:00Z"));
        assertThat(before.all().scheduledStartAt()).isEqualTo(before.current().scheduledStartAt());
        HeroAuctionService at = service(true, "2026-11-16", "2026-11-15T15:00:00Z");
        assertThat(at.current().enabled()).isTrue();
        assertThat(at.current().scheduledStartAt()).isNull();
        HeroAuctionService after = service(true, "2026-11-16", "2026-11-16T00:00:00Z");
        assertThat(after.all().enabled()).isTrue();
        assertThat(after.all().scheduledStartAt()).isNull();
    }

    private static HeroAuctionService service(boolean enabled, String startsOn, String now) {
        return new HeroAuctionService(
                mock(HeroRepository.class),
                mock(JdbcTemplate.class),
                mock(EntityManager.class),
                mock(ExperienceWalletService.class),
                mock(HeroContentService.class),
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC),
                enabled,
                startsOn);
    }
}
