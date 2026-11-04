package until.the.eternity.das.search.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import until.the.eternity.das.search.dto.UserSearchSnapshot;

@Slf4j
@Component
public class RedisUserSearchSnapshotStore implements UserSearchSnapshotStore {

    private static final TypeReference<List<UserSearchSnapshot>> SNAPSHOT_TYPE =
            new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Counter readFailureCounter;
    private final Counter writeFailureCounter;

    public RedisUserSearchSnapshotStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.readFailureCounter =
                Counter.builder("memonogi.search.cache.read.failures")
                        .description("User search Redis read failures")
                        .register(meterRegistry);
        this.writeFailureCounter =
                Counter.builder("memonogi.search.cache.write.failures")
                        .description("User search Redis write failures")
                        .register(meterRegistry);
    }

    @Value("${app.search.cache-key:auth:total-search:user-snapshot:v1}")
    private String cacheKey;

    @Value("${app.search.ttl:PT24H}")
    private Duration ttl;

    @Override
    public Optional<List<UserSearchSnapshot>> read() {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(List.copyOf(objectMapper.readValue(json, SNAPSHOT_TYPE)));
        } catch (Exception exception) {
            readFailureCounter.increment();
            log.warn("User search Redis read failed; falling back to the database", exception);
            return Optional.empty();
        }
    }

    @Override
    public boolean replace(List<UserSearchSnapshot> snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(cacheKey, json, ttl);
            return true;
        } catch (Exception exception) {
            writeFailureCounter.increment();
            log.warn(
                    "User search Redis refresh failed; the previous snapshot is retained",
                    exception);
            return false;
        }
    }
}
