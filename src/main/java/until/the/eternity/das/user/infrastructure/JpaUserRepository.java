package until.the.eternity.das.user.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import until.the.eternity.das.user.entity.User;

public interface JpaUserRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  boolean existsByNickname(String nickname);

  Optional<User> findByEmail(String email);

}
