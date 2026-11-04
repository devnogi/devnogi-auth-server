package until.the.eternity.das.search.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import until.the.eternity.das.search.dto.UserSearchSnapshot;
import until.the.eternity.das.search.infrastructure.UserSearchSnapshotStore;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSearchSnapshotStore snapshotStore;

    private UserSearchService service;

    @BeforeEach
    void setUp() {
        service = new UserSearchService(userRepository, snapshotStore);
    }

    @Test
    void ranksExactThenPrefixThenPartialWhilePreservingSnapshotOrder() {
        when(snapshotStore.read())
                .thenReturn(
                        Optional.of(
                                List.of(
                                        snapshot(1L, "검은별", "류트"),
                                        snapshot(2L, "별", "하프"),
                                        snapshot(3L, "별빛", "울프"),
                                        snapshot(4L, "새벽별", "만돌린"))));

        var results = service.search(" 별 ", 10);

        assertThat(results).extracting(result -> result.userId()).containsExactly(2L, 3L, 1L, 4L);
        assertThat(results.get(0).type()).isEqualTo("USER");
    }

    @Test
    void fallsBackToRecentUsersAndRepopulatesRedisOnCacheMiss() {
        when(snapshotStore.read()).thenReturn(Optional.empty());
        when(userRepository.findRecentSearchableUsers())
                .thenReturn(
                        List.of(User.builder().id(10L).nickname("메모노기").serverName("류트").build()));

        var results = service.search("메모", 10);

        assertThat(results).extracting(result -> result.userId()).containsExactly(10L);
        verify(snapshotStore).replace(List.of(snapshot(10L, "메모노기", "류트")));
    }

    @Test
    void limitsResultsToTenEvenForAnOversizedInternalRequest() {
        when(snapshotStore.read())
                .thenReturn(
                        Optional.of(
                                java.util.stream.LongStream.rangeClosed(1, 15)
                                        .mapToObj(id -> snapshot(id, "검색" + id, null))
                                        .toList()));

        assertThat(service.search("검색", 100)).hasSize(10);
    }

    private static UserSearchSnapshot snapshot(Long id, String nickname, String serverName) {
        return new UserSearchSnapshot(id, nickname, serverName);
    }
}
