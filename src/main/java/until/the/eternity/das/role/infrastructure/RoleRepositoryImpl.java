package until.the.eternity.das.role.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.RoleRepository;
import until.the.eternity.das.role.entity.enums.Name;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

  private final JpaRoleRepository jpaRepository;

  @Override
  public Optional<Role> findByName(Name name) {
    return jpaRepository.findByName(name);
  }
}
