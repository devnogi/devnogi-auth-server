package until.the.eternity.das.role.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Name {
    USER("사용자"),
    ADMIN("관리자");

    private final String label;
}
