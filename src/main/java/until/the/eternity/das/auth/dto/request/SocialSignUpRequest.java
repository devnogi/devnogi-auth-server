package until.the.eternity.das.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record SocialSignUpRequest(
  String provider,

  String providerUserId,

  @Schema(description = "사용자 이메일 (로그인ID)", example = "abc1234@gmail.com", requiredMode = REQUIRED)
  @NotBlank(message = "사용자 이메일은 필수 입력값입니다.")
  String email,

  @Schema(description = "사용자 닉네임 (커뮤니티 표시용)", example = "abc1234", requiredMode = REQUIRED)
  @NotBlank(message = "닉네임을 입력해주세요.")
  String nickname
) {
}
