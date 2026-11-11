package until.the.eternity.das.user.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import until.the.eternity.das.user.entity.User;

@ExtendWith(MockitoExtension.class)
class UserInfoOutboxServiceTest {
    @Mock UserInfoOutboxRepository outboxes;
    @Mock EntityManager entityManager;
    @Mock Query gradeQuery;
    @Mock Query arcanaQuery;
    Clock clock = Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void persistedGradeQuery() {
        when(entityManager.createNativeQuery("SELECT grade FROM users WHERE id = :id FOR UPDATE"))
                .thenReturn(gradeQuery);
        when(gradeQuery.setParameter("id", 42L)).thenReturn(gradeQuery);
        when(entityManager.createNativeQuery(
                        "SELECT arcana FROM user_game_profile WHERE user_id = :id FOR UPDATE",
                        String.class))
                .thenReturn(arcanaQuery);
        when(arcanaQuery.setParameter("id", 42L)).thenReturn(arcanaQuery);
    }

    @Test
    void firstSnapshotStartsAtRevisionOne() {
        User user = user("first", false);
        when(outboxes.findByUserIdForUpdate(42L)).thenReturn(Optional.empty());
        UserInfoOutboxService service = new UserInfoOutboxService(outboxes, clock, entityManager);

        assertEquals(1L, service.enqueue(user));
        verify(entityManager).lock(user, LockModeType.PESSIMISTIC_WRITE);
        verify(outboxes).save(org.mockito.ArgumentMatchers.argThat(row -> row.getRevision() == 1));
    }

    @Test
    void laterSnapshotAdvancesRevisionAndKeepsLatestValues() {
        User original = user("before", false);
        UserInfoOutbox row =
                UserInfoOutbox.first(
                        original,
                        java.time.LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        User changed = user("after", true);
        when(outboxes.findByUserIdForUpdate(42L)).thenReturn(Optional.of(row));
        UserInfoOutboxService service = new UserInfoOutboxService(outboxes, clock, entityManager);

        assertEquals(2L, service.enqueue(changed));
        assertEquals("after", row.getNickname());
        assertEquals(true, row.isVerified());
    }

    private User user(String nickname, boolean verified) {
        return User.builder()
                .id(42L)
                .nickname(nickname)
                .profileImageUrl(null)
                .serverName(null)
                .verified(verified)
                .build();
    }
}
