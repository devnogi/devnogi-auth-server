package until.the.eternity.das.user.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import until.the.eternity.das.common.response.CommonResponse;
import until.the.eternity.das.user.application.UserService;
import until.the.eternity.das.user.dto.request.UserInfoUpdateRequest;
import until.the.eternity.das.user.dto.response.UserInfoResponse;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  /**
   * 사용자 정보 수정 API
   *
   * @param request 사용자정보 수정 요청 정보(닉네임)
   * @param id 수정을 요청한 사용자의 ID값(필터를 통해 자동 주입)
   * @return 수정 완료 여부(Boolean)
   */
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

  /**
   * 사용자 정보 조회 API
   *
   * @param id 정보 조회를 요청한 사용자의 ID값(필터를 통해 자동주입)
   * @return 요청한 사용자의 정보
   */
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

  /**
   * 회원 탈퇴 API
   *
   * @param id 탈퇴를 요청한 사용자의 ID값(필터를 통해 자동주입)
   * @return 탈퇴 성공 여부(Boolean)
   */
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
}
