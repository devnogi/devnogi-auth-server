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
import until.the.eternity.das.common.application.EmailService;
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
import until.the.eternity.das.token.entity.EmailVerificationToken;
import until.the.eternity.das.token.entity.EmailVerificationTokenRepository;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.Status;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final AuthConverter authConverter;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final AccountLockRepository accountLockRepository;
  private final LoginHistoryRepository loginHistoryRepository;
  private final EmailVerificationTokenRepository emailVerificationTokenRepository;
  private final BCryptPasswordEncoder bCryptPasswordEncoder;
  private final JwtUtil jwtUtil;
  private final TokenService tokenService;
  private final S3Service s3Service;
  private final EmailService emailService;

  @Transactional
  public SignUpResponse signUpUser(SignUpRequest request) {

    Role userRole = roleRepository.findByName(Name.USER)
      .orElseThrow(() -> {
        log.error("USER Role이 DB에 존재하지 않습니다.");
        return new CustomException(GlobalExceptionCode.USER_ROLE_NOT_EXISTS);
      });

    return signUp(request, userRole);
  }


  @Transactional
  public SignUpResponse signUpAdmin(SignUpRequest request) {

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

    if (user.getStatus() == Status.PENDING) {
      throw new CustomException(GlobalExceptionCode.EMAIL_NOT_VERIFIED);
    }

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
      saveLoginHistory(user, false, Reason.LOCKED_ACCOUNT, clientIp, userAgent);
      throw new CustomException(GlobalExceptionCode.ACCOUNT_LOCKED);
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
    accountLock.increaseFailAttempts();

    if (accountLock.getFailedAttempts() >= 5) {
      accountLock.lockAccount();
    }

    saveLoginHistory(user, false, Reason.WRONG_PASSWORD, ip, userAgent);
  }

  /**
   * 로그인 성공 핸들링
   */
  private void handleLoginSuccess(User user, AccountLock accountLock, String ip, String userAgent) {
    accountLock.reset();
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
    isValidEmailFormat(request.email());
    if (userRepository.existsByEmail(request.email())) {
      throw new CustomException(GlobalExceptionCode.EMAIL_ALREADY_EXISTS);
    }

    isValidNicknameFormat(request.nickname());
    if (userRepository.existsByNickname(request.nickname())) {
      throw new CustomException(GlobalExceptionCode.NICKNAME_ALREADY_EXISTS);
    }

    isValidPasswordFormat(request.password());

    String profileImageUrl = null;
    if (request.file() != null) {
      String dirName = "profile";
      profileImageUrl = s3Service.uploadImage(request.file(), dirName);
    }

    User user = authConverter.fromUserSignUpRequestToUser(request,
      bCryptPasswordEncoder.encode(request.password()), role, profileImageUrl);

    userRepository.save(user);

    createAndSendVerificationToken(user);

    return authConverter.fromUserToUserSignUpResponse(user);
  }

  private void createAndSendVerificationToken(User user) {
    String token = UUID.randomUUID()
      .toString();
    EmailVerificationToken verificationToken = EmailVerificationToken.builder()
      .user(user)
      .token(token)
      .type(EmailVerificationToken.TokenType.EMAIL_VERIFICATION)
      .expiresAt(LocalDateTime.now()
        .plusHours(24))
      .build();

    emailVerificationTokenRepository.save(verificationToken);
    emailService.sendVerificationEmail(user.getEmail(), token);
  }

  @Transactional
  public void verifyEmail(String token) {
    EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.INVALID_TOKEN));

    if (verificationToken.getExpiresAt()
      .isBefore(LocalDateTime.now())) {
      throw new CustomException(GlobalExceptionCode.TOKEN_EXPIRED);
    }

    User user = verificationToken.getUser();
    user.updateUserStatus(Status.ACTIVE);

    emailVerificationTokenRepository.delete(verificationToken);
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
