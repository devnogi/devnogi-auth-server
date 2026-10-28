package until.the.eternity.das.token.entity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import until.the.eternity.das.user.entity.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByUser(User user);

    /**
     * 사용자의 모든 유효한 리프레시 토큰을 조회
     *
     * @param user 사용자 엔티티
     * @return List<RefreshToken>
     */
    List<RefreshToken> findAllByUserAndRevokedFalse(User user);

    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);

    @Modifying(flushAutomatically = true)
    @Query(
            "update RefreshToken token set token.revoked = true "
                    + "where token.token = :token and token.user = :user and token.revoked = false")
    int revokeActiveToken(@Param("token") String token, @Param("user") User user);
}
