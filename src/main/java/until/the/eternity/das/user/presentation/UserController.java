package until.the.eternity.das.user.presentation;

import static org.springframework.http.HttpStatus.CREATED;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import until.the.eternity.das.user.application.UserService;
import until.the.eternity.das.user.dto.request.SignUpRequest;
import until.the.eternity.das.user.dto.response.SignUpResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class UserController {

  private final UserService userService;

  /**
   * 회원 가입 API
   *
   * @param request 회원 가입 요청 정보 (이메일, 비밀번호, 닉네임)
   * @return 생성된 회원 정보가 포함된 ResponseEntity 객체 (HTTP 상태 201 Created)
   */
  @PostMapping("/signup")
  @Operation(summary = "회원 가입 API", description = """
      - Description : 이 API는 회원 가입을 요청합니다.
      - Assignee : 안나
      """)
  @ApiResponse(
      responseCode = "201",
      content = @Content(schema = @Schema(implementation = SignUpResponse.class)))
  public ResponseEntity<SignUpResponse> signUp(@RequestBody SignUpRequest request) {
    return ResponseEntity.status(CREATED).body(userService.signUp(request));
  }


  /**
   * 이메일 중복 체크 API
   *
   * @param email 체크할 이메일 (쿼리 파라미터)
   * @return 이메일 사용 가능 여부
   */
  @GetMapping("/check-email")
  @Operation(summary = "이메일 중복 확인 API", description = """
      - Description : 입력한 이메일이 이미 가입되어 있는지 확인합니다.
      - 사용 가능한 이메일이면 true, 중복된 이메일이면 false를 반환합니다.
      - Assignee : 안나
      """)
  public ResponseEntity<Boolean> checkEmail(
      @RequestParam(name = "email") @NotBlank String email) {

    // 이메일 형식 유효성 검증
    userService.isValidEmailFormat(email);

    boolean exists = userService.existsByEmail(email);
    // exists == true면 이미 사용중인 이메일
    return ResponseEntity.ok(!exists); // true = 사용 가능, false = 중복됨
  }

  /**
   * 닉네임 중복 체크 API
   *
   * @param nickname 체크할 닉네임 (쿼리 파라미터)
   * @return 닉네임 사용 가능 여부
   */
  @GetMapping("/check-nickname")
  @Operation(summary = "닉네임 중복 확인 API", description = """
      - Description : 입력한 닉네임이 이미 사용 중인지 확인합니다.
      - 사용 가능한 닉네임이면 true, 중복된 닉네임이면 false를 반환합니다.
      - Assignee : 안나
      """)
  public ResponseEntity<Boolean> checkNickname(
      @RequestParam(name = "nickname") @NotBlank String nickname) {

    // 닉네임 유효성 검증
    userService.isValidNicknameFormat(nickname);

    boolean exists = userService.existsByNickname(nickname);
    // exists == true면 이미 사용중인 닉네임
    return ResponseEntity.ok(!exists); // true = 사용 가능, false = 중복됨
  }


}
