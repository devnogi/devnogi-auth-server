package until.the.eternity.das.verification.entity;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVerificationTokenRepository extends JpaRepository<UserVerificationToken, Long> {

  Optional<UserVerificationToken> findTopByUserIdAndRevokedFalseAndVerifiedFalseAndExpiresAtAfterOrderByIssuedAtDesc(
    Long userId,
    LocalDateTime now
  );

  Optional<UserVerificationToken> findTopByUserIdOrderByIssuedAtDesc(Long userId);

  Optional<UserVerificationToken> findByTokenValue(String tokenValue);

  boolean existsByTokenValue(String tokenValue);
}
