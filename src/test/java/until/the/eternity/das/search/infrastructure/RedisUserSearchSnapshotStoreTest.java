package until.the.eternity.das.search.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import until.the.eternity.das.search.dto.UserSearchSnapshot;

@ExtendWith(MockitoExtension.class)
class RedisUserSearchSnapshotStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RedisUserSearchSnapshotStore store;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        store = new RedisUserSearchSnapshotStore(redisTemplate, new ObjectMapper(), meterRegistry);
        ReflectionTestUtils.setField(store, "cacheKey", "test:user-search");
        ReflectionTestUtils.setField(store, "ttl", Duration.ofHours(24));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void replacesTheWholeSnapshotWithTheConfiguredTtl() {
        var snapshot = List.of(new UserSearchSnapshot(1L, "메모노기", "류트"));

        assertThat(store.replace(snapshot)).isTrue();

        verify(valueOperations)
                .set(
                        "test:user-search",
                        "[{\"userId\":1,\"nickname\":\"메모노기\",\"serverName\":\"류트\"}]",
                        Duration.ofHours(24));
    }

    @Test
    void treatsRedisFailureAsACacheMiss() {
        when(valueOperations.get("test:user-search")).thenThrow(new IllegalStateException("down"));

        assertThat(store.read()).isEmpty();
        assertThat(meterRegistry.counter("memonogi.search.cache.read.failures").count())
                .isEqualTo(1);
    }

    @Test
    void countsRedisWriteFailuresWithoutDeletingThePreviousSnapshot() {
        var snapshot = List.of(new UserSearchSnapshot(1L, "메모노기", "류트"));
        org.mockito.Mockito.doThrow(new IllegalStateException("down"))
                .when(valueOperations)
                .set(
                        org.mockito.ArgumentMatchers.eq("test:user-search"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(Duration.ofHours(24)));

        assertThat(store.replace(snapshot)).isFalse();
        assertThat(meterRegistry.counter("memonogi.search.cache.write.failures").count())
                .isEqualTo(1);
        verify(redisTemplate, org.mockito.Mockito.never()).delete("test:user-search");
    }
}
