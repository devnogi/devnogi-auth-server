package until.the.eternity.das.user.entity.enums;

import lombok.*;

@Getter
@RequiredArgsConstructor
public enum Status {
    ACTIVE("활성화"),
    INACTIVE("비활성화"),
    BANNED("정지됨");

    private final String label;
}
