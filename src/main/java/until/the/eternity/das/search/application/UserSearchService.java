package until.the.eternity.das.search.application;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.search.dto.UserSearchResponse;
import until.the.eternity.das.search.dto.UserSearchSnapshot;
import until.the.eternity.das.search.infrastructure.UserSearchSnapshotStore;
import until.the.eternity.das.user.entity.UserRepository;

@Service
@RequiredArgsConstructor
public class UserSearchService {

    private static final int RESULT_LIMIT = 10;

    private final UserRepository userRepository;
    private final UserSearchSnapshotStore snapshotStore;

    @Transactional(readOnly = true)
    public List<UserSearchResponse> search(String query, int limit) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }

        int boundedLimit = Math.min(Math.max(limit, 1), RESULT_LIMIT);
        return snapshot().stream()
                .filter(user -> normalize(user.nickname()).contains(normalizedQuery))
                .sorted(
                        Comparator.comparingInt(
                                user -> matchRank(user.nickname(), normalizedQuery)))
                .limit(boundedLimit)
                .map(UserSearchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean refreshSnapshot() {
        return snapshotStore.replace(loadSnapshot());
    }

    private List<UserSearchSnapshot> snapshot() {
        return snapshotStore
                .read()
                .orElseGet(
                        () -> {
                            List<UserSearchSnapshot> loaded = loadSnapshot();
                            snapshotStore.replace(loaded);
                            return loaded;
                        });
    }

    private List<UserSearchSnapshot> loadSnapshot() {
        return userRepository.findRecentSearchableUsers().stream()
                .map(
                        user ->
                                new UserSearchSnapshot(
                                        user.getId(), user.getNickname(), user.getServerName()))
                .toList();
    }

    private static int matchRank(String nickname, String normalizedQuery) {
        String normalizedNickname = normalize(nickname);
        if (normalizedNickname.equals(normalizedQuery)) {
            return 0;
        }
        return normalizedNickname.startsWith(normalizedQuery) ? 1 : 2;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
