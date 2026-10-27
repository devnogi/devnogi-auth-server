package until.the.eternity.das.verification.entity;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserVerificationTokenRepository extends JpaRepository<UserVerificationToken, Long> {

  Optional<UserVerificationToken> findTopByUserIdAndRevokedFalseAndVerifiedFalseAndExpiresAtAfterOrderByIssuedAtDesc(
    Long userId,
    LocalDateTime now
  );

  Optional<UserVerificationToken> findTopByUserIdOrderByIssuedAtDescIdDesc(Long userId);

  Optional<UserVerificationToken> findByTokenValue(String tokenValue);

  @Query("select t.user.id from UserVerificationToken t where t.tokenValue = :value")
  Optional<Long> findUserIdByTokenValue(@Param("value") String value);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from UserVerificationToken t where t.tokenValue = :value")
  Optional<UserVerificationToken> findByTokenValueForUpdate(@Param("value") String value);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from UserVerificationToken t where t.user.id = :userId and t.revoked = false and t.verified = false and t.expiresAt > :now")
  List<UserVerificationToken> findActiveForUpdate(@Param("userId") Long userId, @Param("now") LocalDateTime now);

  boolean existsByTokenValue(String tokenValue);
}
