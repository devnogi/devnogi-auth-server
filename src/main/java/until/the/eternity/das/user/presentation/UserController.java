package until.the.eternity.das.user.presentation;

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
  public ResponseEntity<CommonResponse<Boolean>> updateInfo(
    @ModelAttribute UserInfoUpdateRequest request,
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userService.updateUserInfo(request, id)));
  }

  @GetMapping("/info")
  public ResponseEntity<CommonResponse<UserInfoResponse>> getInfo(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userService.getUserInfo(id)));
  }

  @PatchMapping("/withdraw")
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
