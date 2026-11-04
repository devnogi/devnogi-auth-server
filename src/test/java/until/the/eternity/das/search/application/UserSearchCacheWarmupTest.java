package until.the.eternity.das.search.application;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSearchCacheWarmupTest {

    @Mock private UserSearchService userSearchService;

    @Test
    void warmsTheCacheAtStartupAndOnTheScheduledRefresh() {
        var warmup = new UserSearchCacheWarmup(userSearchService);

        warmup.run(null);
        warmup.refreshHourly();

        verify(userSearchService, org.mockito.Mockito.times(2)).refreshSnapshot();
    }
}
