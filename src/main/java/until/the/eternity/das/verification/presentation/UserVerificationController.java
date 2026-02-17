package until.the.eternity.das.verification.presentation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import until.the.eternity.das.common.response.CommonResponse;
import until.the.eternity.das.verification.application.UserVerificationService;
import until.the.eternity.das.verification.dto.response.UserVerificationHistoryListResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationInfoResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationTokenIssueResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationTokenResponse;

@RestController
@RequestMapping("/api/user/verification")
@RequiredArgsConstructor
public class UserVerificationController {

  private final UserVerificationService userVerificationService;

  @PostMapping("/token")
  public ResponseEntity<CommonResponse<UserVerificationTokenIssueResponse>> issueToken(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.issueToken(id)));
  }

  @GetMapping("/token")
  public ResponseEntity<CommonResponse<UserVerificationTokenResponse>> getMyToken(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.getMyToken(id)));
  }

  @PostMapping("/token/reissue")
  public ResponseEntity<CommonResponse<UserVerificationTokenIssueResponse>> reissueToken(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.reissueToken(id)));
  }

  @GetMapping("/info")
  public ResponseEntity<CommonResponse<UserVerificationInfoResponse>> getMyVerificationInfo(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.getMyVerificationInfo(id)));
  }

  @GetMapping("/history")
  public ResponseEntity<CommonResponse<UserVerificationHistoryListResponse>> getMyVerificationHistory(
    @AuthenticationPrincipal Long id,
    @RequestParam(defaultValue = "latest") String sort,
    @RequestParam(defaultValue = "20") Integer limit
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.getMyVerificationHistories(id, sort, limit)));
  }

  @GetMapping("/users/{userId}/info")
  public ResponseEntity<CommonResponse<UserVerificationInfoResponse>> getUserVerificationInfo(
    @PathVariable Long userId
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.getUserVerificationInfo(userId)));
  }
}
