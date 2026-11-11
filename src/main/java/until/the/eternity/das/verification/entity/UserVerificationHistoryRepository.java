package until.the.eternity.das.verification.entity;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserVerificationHistoryRepository
        extends JpaRepository<UserVerificationHistory, Long> {

    @Query(
            value =
                    "SELECT * FROM user_verification_history WHERE user_id = :userId AND experience_baseline = FALSE ORDER BY verified_at DESC LIMIT 100",
            nativeQuery = true)
    List<UserVerificationHistory> findTop100ByUserIdOrderByVerifiedAtDesc(
            @Param("userId") Long userId);

    @Modifying
    @Query(
            "update UserVerificationHistory h set h.validUntil = :until where h.user.id = :userId and h.experienceValidFrom is not null and h.validUntil is null")
    int closeExperienceIntervals(@Param("userId") Long userId, @Param("until") LocalDateTime until);
}
