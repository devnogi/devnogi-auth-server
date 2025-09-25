package until.the.eternity.das.token.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import until.the.eternity.das.user.entity.User;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByUser(User user);

  /**
   * 사용자의 모든 유효한 리프레시 토큰을 조회
   *
   * @param user 사용자 엔티티
   * @return List<RefreshToken>
   */
  List<RefreshToken> findAllByUserAndRevokedFalse(User user);
}