package until.the.eternity.das.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import until.the.eternity.das.token.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
}
