package until.the.eternity.das.user.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  boolean existsByNickname(String nickname);

  boolean existsByNicknameAndIdNot(String nickname, Long id);

  User save(User user);

  Optional<User> findByEmail(String email);

  Optional<User> findById(Long id);

}
