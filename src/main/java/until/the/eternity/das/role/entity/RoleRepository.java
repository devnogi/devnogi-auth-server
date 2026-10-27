package until.the.eternity.das.role.entity;

import until.the.eternity.das.role.entity.enums.Name;

import java.util.Optional;

public interface RoleRepository {
  
  Optional<Role> findByName(Name name);

}
