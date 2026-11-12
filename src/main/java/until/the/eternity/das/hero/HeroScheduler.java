package until.the.eternity.das.hero;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeroScheduler {
    private final HeroAuctionService auctions;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${hero.auctions.reconcile-delay-ms:60000}")
    public void reconcile() {
        try {
            auctions.createCurrentWeek();
            for (var week : auctions.pendingWeeks()) auctions.settle(week);
        } catch (RuntimeException e) {
            log.error("Hero auction reconciliation failed; retry scheduled", e);
        }
    }
}
