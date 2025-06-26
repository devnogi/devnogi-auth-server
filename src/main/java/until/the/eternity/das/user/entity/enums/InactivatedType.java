package until.the.eternity.das.user.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InactivatedType {
    WITHDRAWN("탈퇴"),
    INACTIVE_TOO_LONG("장기미접속"),
    SUSPICIOUS_IP("불특정IP접속");

    private final String label;
}
