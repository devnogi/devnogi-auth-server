package until.the.eternity.das.user.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import until.the.eternity.das.common.response.CommonResponse;
import until.the.eternity.das.user.application.UserService;
import until.the.eternity.das.user.dto.request.UserInfoUpdateRequest;
import until.the.eternity.das.user.dto.response.UserInfoResponse;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PutMapping("/info")
  @Operation(summary = "사용자 정보 수정 API", description = """
    - Description : 이 API는 사용자 정보 수정을 요청합니다.
    수정이 정상적으로 완료되었다면 True를 반환합니다.
    - Assignee : 장욱
    """)
  public ResponseEntity<CommonResponse<Boolean>> updateInfo(
    @ModelAttribute UserInfoUpdateRequest request,
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userService.updateUserInfo(request, id)));
  }

  @GetMapping("/info")
  @Operation(summary = "사용자 정보 조회 API", description = """
    - Description : 이 API는 사용자 정보 조회를 요청합니다.
    - Assignee : 장욱
    """)
  @ApiResponse(
    responseCode = "200",
    content = @Content(schema = @Schema(implementation = UserInfoResponse.class)))
  public ResponseEntity<CommonResponse<UserInfoResponse>> getInfo(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userService.getUserInfo(id)));
  }

  @PatchMapping("/withdraw")
  @Operation(summary = "회원 탈퇴 API", description = """
    - Description : 이 API는 회원 탈퇴를 요청합니다.
    회원탈퇴가 정상적으로 완료되었다면 True를 반환합니다.
    - Assignee : 장욱
    """)
  public ResponseEntity<CommonResponse<Boolean>> withdrawUser(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userService.withdrawUser(id)));
  }

  // Todo 비밀번호 변경 기능 만들기
//  @PatchMapping("password")
//  public ResponseEntity<CommonResponse<Boolean>> updatePassword() {
//    return null;
//  }

}
