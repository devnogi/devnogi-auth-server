package until.the.eternity.das.verification.entity;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserVerificationRepository extends JpaRepository<UserVerification, Long> {

    Optional<UserVerification> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from UserVerification v where v.user.id = :userId")
    Optional<UserVerification> findByUserIdForUpdate(@Param("userId") Long userId);

    @Query("select u.id from User u where u.serverName = :server and u.nickname = :nickname")
    Optional<Long> findProfileOwnerId(
            @Param("server") String server, @Param("nickname") String nickname);

    Optional<UserVerification> findByServerNameAndCharacterNameAndVerifiedTrue(
            String serverName, String characterName);
}
