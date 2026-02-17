package until.the.eternity.das.verification.entity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVerificationRepository extends JpaRepository<UserVerification, Long> {

  Optional<UserVerification> findByUserId(Long userId);

  Optional<UserVerification> findByServerNameAndCharacterNameAndVerifiedTrue(String serverName, String characterName);
}
