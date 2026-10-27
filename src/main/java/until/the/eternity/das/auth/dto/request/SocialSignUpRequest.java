package until.the.eternity.das.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record SocialSignUpRequest(
  String provider,

  String providerUserId,

  @Schema(description = "구형 클라이언트 호환 필드. 신원 출처로 사용하지 않음")
  String email,

  @Schema(description = "사용자 닉네임 (커뮤니티 표시용)", example = "abc1234", requiredMode = REQUIRED)
  String nickname,

  @Schema(description = "랜덤 닉네임 후보 발급 티켓")
  String nicknameTicket,

  @Schema(description = "OAuth 교환 뒤 서버가 발급한 일회용 가입 증명")
  String signupProof,

  MultipartFile file
) {
}
