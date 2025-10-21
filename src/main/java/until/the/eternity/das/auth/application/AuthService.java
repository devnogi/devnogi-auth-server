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
import until.the.eternity.das.common.aop.ActiveUserRequired;
import until.the.eternity.das.common.application.S3Service;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.RoleRepository;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final AuthConverter authConverter;
  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
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
  @ActiveUserRequired
  public LoginResultResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(request.email())
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

    if (!bCryptPasswordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new CustomException(GlobalExceptionCode.INVALID_PASSWORD);
    }

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
