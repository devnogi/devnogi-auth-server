package until.the.eternity.das.user.entity;

import java.util.Optional;

public interface UserRepository {

  boolean existsByEmail(String email);

  boolean existsByNickname(String nickname);

  User save(User user);

  Optional<User> findByEmail(String email);
}
