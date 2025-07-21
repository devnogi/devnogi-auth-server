package until.the.eternity.das.role.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.enums.Name;

public interface JpaRoleRepository extends JpaRepository<Role, Long> {

  Optional<Role> findByName(Name name);
  
}
