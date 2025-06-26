package until.the.eternity.das.login.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Reason {
    WRONG_PASSWORD("잘못된 비밀번호"),
    LOCKED_ACCOUNT("잠긴 계정");

    private final String label;
}
