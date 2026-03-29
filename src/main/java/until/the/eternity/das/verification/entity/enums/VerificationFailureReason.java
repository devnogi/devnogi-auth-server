package until.the.eternity.das.verification.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VerificationFailureReason {
  TOKEN_NOT_FOUND("토큰을 찾을 수 없음"),
  TOKEN_REVOKED("토큰이 폐기됨"),
  TOKEN_EXPIRED("토큰이 만료됨"),
  TOKEN_ALREADY_VERIFIED("이미 인증된 토큰"),
  INVALID_MESSAGE("메시지 형식이 올바르지 않음"),
  NICKNAME_DUPLICATED("캐릭터명이 이미 사용 중임");

  private final String description;
}
