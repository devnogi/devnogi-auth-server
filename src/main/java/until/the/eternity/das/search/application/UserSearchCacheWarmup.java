package until.the.eternity.das.search.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.search.warmup-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class UserSearchCacheWarmup implements ApplicationRunner {

    private final UserSearchService userSearchService;

    @Override
    public void run(ApplicationArguments args) {
        refresh("startup");
    }

    @Scheduled(cron = "${app.search.refresh-cron:0 0 * * * *}", zone = "Asia/Seoul")
    public void refreshHourly() {
        refresh("scheduled");
    }

    private void refresh(String trigger) {
        try {
            boolean cached = userSearchService.refreshSnapshot();
            if (!cached) {
                log.warn(
                        "User search {} refresh loaded the database but could not update Redis",
                        trigger);
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "User search {} refresh failed; the previous Redis snapshot is retained",
                    trigger,
                    exception);
        }
    }
}
