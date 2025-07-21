package until.the.eternity.das.role.entity;

import java.util.Optional;
import until.the.eternity.das.role.entity.enums.Name;

public interface RoleRepository {
  
  Optional<Role> findByName(Name name);

}
