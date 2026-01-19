package until.the.eternity.das.auth.application;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.auth.dto.response.LoginResultResponse;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.login.entity.AccountLock;
import until.the.eternity.das.login.entity.AccountLockRepository;
import until.the.eternity.das.login.entity.LoginHistory;
import until.the.eternity.das.login.entity.LoginHistoryRepository;
import until.the.eternity.das.login.entity.enums.Reason;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.RoleRepository;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final AuthConverter authConverter;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final AccountLockRepository accountLockRepository;
  private final LoginHistoryRepository loginHistoryRepository;
  private final BCryptPasswordEncoder bCryptPasswordEncoder;
  private final JwtUtil jwtUtil;
  private final TokenService tokenService;
  private final S3Service s3Service;


  @Transactional
  public SignUpResponse signUpUser(SignUpRequest request) {

    // 기본 USER Role이 존재하는지 확인
    Role userRole = roleRepository.findByName(Name.USER)
      .orElseThrow(() -> {
        log.error("USER Role이 DB에 존재하지 않습니다.");
        return new CustomException(GlobalExceptionCode.USER_ROLE_NOT_EXISTS);
      });

    return signUp(request, userRole);
  }


  @Transactional
  public SignUpResponse signUpAdmin(SignUpRequest request) {

    // Admin Role이 존재하는지 확인
    Role adminRole = roleRepository.findByName(Name.ADMIN)
      .orElseThrow(() -> {
        log.error("ADMIN Role이 DB에 존재하지 않습니다.");
        return new CustomException(GlobalExceptionCode.ADMIN_ROLE_NOT_EXISTS);
      });

    return signUp(request, adminRole);
  }

  @Transactional
  public LoginResultResponse login(LoginRequest request, String clientIp, String userAgent) {
    User user = userRepository.findByEmail(request.email())
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

    AccountLock accountLock = accountLockRepository.findById(user.getId())
      .orElseGet(() -> {
        AccountLock newLock = AccountLock.builder()
          .user(user)
          .userId(user.getId())
          .failedAttempts(0)
          .updatedAt(LocalDateTime.now())
          .build();
        return accountLockRepository.save(newLock);
      });

    if (accountLock.getLockedUntil() != null && accountLock.getLockedUntil()
      .isAfter(LocalDateTime.now())) {
      // 잠금 상태인 경우 이력 저장 후 예외 발생
      saveLoginHistory(user, false, Reason.LOCKED_ACCOUNT, clientIp, userAgent); // Reason에 LOCKED_ACCOUNT가 없으면 추가 필요
      throw new CustomException(GlobalExceptionCode.ACCOUNT_LOCKED); // GlobalExceptionCode에 추가 필요
    }

    if (!bCryptPasswordEncoder.matches(request.password(), user.getPasswordHash())) {
      handleLoginFailure(user, accountLock, clientIp, userAgent);
      throw new CustomException(GlobalExceptionCode.INVALID_PASSWORD);
    }

    handleLoginSuccess(user, accountLock, clientIp, userAgent);

    String accessToken = jwtUtil.generateAccessToken(user);
    String refreshToken = jwtUtil.generateRefreshToken(user);

    tokenService.saveNewRefreshToken(user.getId(), refreshToken);
    user.updateLastLoginAt();

    return LoginResultResponse.builder()
      .user(user)
      .accessToken(accessToken)
      .refreshToken(refreshToken)
      .build();
  }

  /**
   * 로그인 실패 핸들링
   */
  private void handleLoginFailure(User user, AccountLock accountLock, String ip, String userAgent) {
    accountLock.increaseFailAttempts(); // 실패 횟수 증가

    // 5회 이상 실패 시 5분 잠금
    if (accountLock.getFailedAttempts() >= 5) {
      accountLock.lockAccount(); // 5분 잠금
    }

    // Dirty Checking으로 AccountLock 업데이트 됨 (Transactional)
    saveLoginHistory(user, false, Reason.WRONG_PASSWORD, ip, userAgent); // Reason.WRONG_PASSWORD 확인 필요
  }

  /**
   * 로그인 성공 핸들링
   */
  private void handleLoginSuccess(User user, AccountLock accountLock, String ip, String userAgent) {
    accountLock.reset(); // 실패 횟수 및 잠금 초기화
    saveLoginHistory(user, true, null, ip, userAgent);
  }

  /**
   * 로그인 이력 저장
   */
  private void saveLoginHistory(User user, boolean success, Reason reason, String ip, String userAgent) {
    LoginHistory history = LoginHistory.builder()
      .user(user)
      .success(success)
      .reason(reason)
      .loginIp(ip)
      .userAgent(userAgent)
      .createdAt(LocalDateTime.now())
      .build();

    loginHistoryRepository.save(history);
  }

  private SignUpResponse signUp(SignUpRequest request, Role role) {
    // 이메일 형식 유효성 검증
    isValidEmailFormat(request.email());
    // 이메일 중복 검증
    if (userRepository.existsByEmail(request.email())) {
      throw new CustomException(GlobalExceptionCode.EMAIL_ALREADY_EXISTS);
    }

    // 닉네임 형식 유효성 검증
    isValidNicknameFormat(request.nickname());
    // 닉네임 중복 검증
    if (userRepository.existsByNickname(request.nickname())) {
      throw new CustomException(GlobalExceptionCode.NICKNAME_ALREADY_EXISTS);
    }

    // 비밀번호 유효성 검증
    isValidPasswordFormat(request.password());

    // 프로필 이미지 등록
    String profileImageUrl = null;
    if (request.file() != null) {
      String dirName = "profile";
      profileImageUrl = s3Service.uploadImage(request.file(), dirName);
    }

    User user = authConverter.fromUserSignUpRequestToUser(request,
      bCryptPasswordEncoder.encode(request.password()), role, profileImageUrl);

    userRepository.save(user);

    return authConverter.fromUserToUserSignUpResponse(user);
  }


  private void isValidPasswordFormat(String password) {
    if (password == null || !password
      .matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,30}$")) {
      throw new CustomException(GlobalExceptionCode.INVALID_PASSWORD_FORMAT);
    }
  }


  public void isValidEmailFormat(@NotBlank String email) {
    if (email == null || !(email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"))) {
      throw new CustomException(GlobalExceptionCode.INVALID_EMAIL_FORMAT);
    }
  }

  public boolean existsByEmail(@NotBlank String email) {
    return userRepository.existsByEmail(email);
  }

  public void isValidNicknameFormat(@NotBlank String nickname) {
    if (nickname == null || !nickname.matches("^[가-힣a-zA-Z0-9]{2,20}$")) {
      throw new CustomException(GlobalExceptionCode.INVALID_NICKNAME_FORMAT);
    }
  }

  public boolean existsByNickname(@NotBlank String nickname) {
    return userRepository.existsByNickname(nickname);
  }
}
