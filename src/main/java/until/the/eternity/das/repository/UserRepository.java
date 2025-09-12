package until.the.eternity.das.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import until.the.eternity.das.user.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
