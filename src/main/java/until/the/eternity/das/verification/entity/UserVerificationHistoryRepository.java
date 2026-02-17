package until.the.eternity.das.verification.entity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserVerificationHistoryRepository extends JpaRepository<UserVerificationHistory, Long> {

  List<UserVerificationHistory> findTop100ByUserIdOrderByVerifiedAtDesc(Long userId);
}
