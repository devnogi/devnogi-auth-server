package until.the.eternity.das.user.entity;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    User save(User user);

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    Optional<User> findById(Long id);

    @Query(
            value =
                    """
                    SELECT u.* FROM users u FORCE INDEX (idx_users_last_login_at_desc)
                    WHERE u.role_id <> 1
                    ORDER BY u.last_login_at DESC, u.id DESC
                    LIMIT 1000
                    """,
            nativeQuery = true)
    List<User> findRecentSearchableUsers();
}
