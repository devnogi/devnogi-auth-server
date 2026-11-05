package until.the.eternity.das.user.outbox;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInfoOutboxRepository extends JpaRepository<UserInfoOutbox, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from UserInfoOutbox o where o.userId = :userId")
    Optional<UserInfoOutbox> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query(
            value =
                    """
                    SELECT *
                    FROM user_info_outbox
                    WHERE published_revision < revision
                      AND next_attempt_at <= :now
                      AND (claim_until IS NULL OR claim_until < :now)
                    ORDER BY next_attempt_at, user_id
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<UserInfoOutbox> findClaimable(
            @Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update UserInfoOutbox o
               set o.publishedRevision =
                       case when o.publishedRevision < :revision then :revision
                            else o.publishedRevision end,
                   o.claimToken = null,
                   o.claimUntil = null,
                   o.attemptCount = 0,
                   o.nextAttemptAt = :now,
                   o.updatedAt = :now
             where o.userId = :userId
               and o.claimToken = :claimToken
            """)
    int markPublished(
            @Param("userId") Long userId,
            @Param("revision") long revision,
            @Param("claimToken") String claimToken,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update UserInfoOutbox o
               set o.claimToken = null,
                   o.claimUntil = null,
                   o.attemptCount = o.attemptCount + 1,
                   o.nextAttemptAt = :nextAttemptAt,
                   o.updatedAt = :now
             where o.userId = :userId
               and o.claimToken = :claimToken
            """)
    int markFailed(
            @Param("userId") Long userId,
            @Param("claimToken") String claimToken,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("now") LocalDateTime now);

    @Query("select count(o) from UserInfoOutbox o where o.publishedRevision < o.revision")
    long countPending();
}
