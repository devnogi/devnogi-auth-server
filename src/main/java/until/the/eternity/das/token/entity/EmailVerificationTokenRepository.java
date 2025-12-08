package until.the.eternity.das.token.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
  Optional<EmailVerificationToken> findByToken(String token);

  Optional<EmailVerificationToken> findByUserId(Long userId);
}
