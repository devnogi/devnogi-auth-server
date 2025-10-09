package until.the.eternity.das.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.multipart.MultipartFile;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record SignUpRequest(

  @Schema(description = "사용자 이메일 (로그인ID)", example = "abc1234@gmail.com", requiredMode = REQUIRED)
  @NotBlank(message = "사용자 이메일은 필수 입력값입니다.")
  String email,

  @Schema(description = "비밀번호", example = "123qwe!@#QWE", requiredMode = REQUIRED)
  @NotBlank(message = "비밀번호를 입력해주세요")
  @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,30}$",
    message = "비밀번호는 8~30 자리이면서 1개 이상의 알파벳, 숫자, 특수문자를 포함해야합니다.")
  String password,

  @Schema(description = "사용자 닉네임 (커뮤니티 표시용)", example = "abc1234", requiredMode = REQUIRED)
  @NotBlank(message = "닉네임을 입력해주세요.")
  String nickname,

  @Schema(description = "프로필 이미지 파일")
  MultipartFile file
) {

}
