package until.the.eternity.das.role.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Name {
  SUPER_ADMIN("최고 권한 관리자"),
  ADMIN("관리자"),
  USER("사용자");

  private final String label;
}
