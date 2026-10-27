package until.the.eternity.das.verification.presentation;

import io.swagger.v3.oas.annotations.Operation;
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
import until.the.eternity.das.verification.dto.response.UserVerificationPublicSummaryResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationTokenIssueResponse;
import until.the.eternity.das.verification.dto.response.UserVerificationTokenResponse;

@RestController
@RequestMapping("/api/user/verification")
@RequiredArgsConstructor
public class UserVerificationController {

  private final UserVerificationService userVerificationService;

  @PostMapping("/token")
  @Operation(
    summary = "사용자 인증 토큰 발급",
    description = "사용자가 최초 인증을 진행할 수 있도록 신규 인증 토큰을 발급합니다."
  )
  public ResponseEntity<CommonResponse<UserVerificationTokenIssueResponse>> issueToken(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.issueToken(id)));
  }

  @GetMapping("/token")
  @Operation(
    summary = "내 인증 토큰 조회",
    description = "가장 최근에 발급된 인증 토큰 정보를 조회합니다."
  )
  public ResponseEntity<CommonResponse<UserVerificationTokenResponse>> getMyToken(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.getMyToken(id)));
  }

  @PostMapping("/token/reissue")
  @Operation(
    summary = "인증 토큰 재발급",
    description = "기존 토큰을 폐기하고 새로운 인증 토큰을 재발급합니다."
  )
  public ResponseEntity<CommonResponse<UserVerificationTokenIssueResponse>> reissueToken(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.reissueToken(id)));
  }

  @GetMapping("/info")
  @Operation(
    summary = "내 인증 상태 조회",
    description = "현재 로그인한 사용자의 인증 여부, 서버명, 캐릭터명 등을 조회합니다."
  )
  public ResponseEntity<CommonResponse<UserVerificationInfoResponse>> getMyVerificationInfo(
    @AuthenticationPrincipal Long id
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.getMyVerificationInfo(id)));
  }

  @GetMapping("/history")
  @Operation(
    summary = "내 인증 이력 조회",
    description = "정렬 기준과 조회 개수를 지정하여 개인 인증 히스토리를 조회합니다."
  )
  public ResponseEntity<CommonResponse<UserVerificationHistoryListResponse>> getMyVerificationHistory(
    @AuthenticationPrincipal Long id,
    @RequestParam(defaultValue = "latest") String sort,
    @RequestParam(defaultValue = "20") Integer limit
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.getMyVerificationHistories(id, sort, limit)));
  }

  @GetMapping("/users/{userId}/info")
  @Operation(
    summary = "사용자 인증 상태 조회",
    description = "특정 사용자 ID에 대한 최신 인증 상태 정보를 조회합니다."
  )
  public ResponseEntity<CommonResponse<UserVerificationInfoResponse>> getUserVerificationInfo(
    @PathVariable Long userId
  ) {
    return ResponseEntity.ok(CommonResponse.success(userVerificationService.getUserVerificationInfo(userId)));
  }

  @GetMapping("/public/users/{userId}/summary")
  @Operation(
    summary = "공개 인증 요약 조회",
    description = "특정 사용자의 현재 인증 상태와 최근 인증 이력을 공개 용도로 조회합니다."
  )
  public ResponseEntity<CommonResponse<UserVerificationPublicSummaryResponse>> getUserVerificationPublicSummary(
    @PathVariable Long userId,
    @RequestParam(defaultValue = "20") Integer limit
  ) {
    return ResponseEntity.ok(
      CommonResponse.success(userVerificationService.getUserVerificationPublicSummary(userId, limit))
    );
  }
}
