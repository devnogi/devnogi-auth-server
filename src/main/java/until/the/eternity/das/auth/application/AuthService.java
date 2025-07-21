package until.the.eternity.das.auth.application;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.auth.dto.request.SignUpRequest;
import until.the.eternity.das.auth.dto.response.SignUpResponse;
import until.the.eternity.das.auth.exception.EmailAlreadyExistsException;
import until.the.eternity.das.auth.exception.InvalidEmailFormatException;
import until.the.eternity.das.auth.exception.InvalidNicknameFormatException;
import until.the.eternity.das.auth.exception.InvalidPasswordFormatException;
import until.the.eternity.das.auth.exception.NicknameAlreadyExistsException;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.RoleRepository;
import until.the.eternity.das.role.entity.enums.Name;
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


  @Transactional
  public SignUpResponse signUpUser(SignUpRequest request) {

    // 기본 USER Role이 존재하는지 확인
    Role userRole = roleRepository.findByName(Name.USER)
        .orElseThrow(() -> {
          log.error("USER Role이 DB에 존재하지 않습니다.");
          return new IllegalStateException("USER Role이 없습니다.");
        });

    return signUp(request, userRole);
  }


  @Transactional
  public SignUpResponse signUpAdmin(SignUpRequest request) {

    // Admin Role이 존재하는지 확인
    Role adminRole = roleRepository.findByName(Name.ADMIN)
        .orElseThrow(() -> {
          log.error("ADMIN Role이 DB에 존재하지 않습니다.");
          return new IllegalStateException("ADMIN Role이 없습니다.");
        });

    return signUp(request, adminRole);
  }

  private SignUpResponse signUp(SignUpRequest request, Role role) {
    // 이메일 형식 유효성 검증
    isValidEmailFormat(request.email());
    // 이메일 중복 검증
    if (userRepository.existsByEmail(request.email())) {
      throw new EmailAlreadyExistsException();
    }

    // 닉네임 형식 유효성 검증
    isValidNicknameFormat(request.nickname());
    // 닉네임 중복 검증
    if (userRepository.existsByNickname(request.nickname())) {
      throw new NicknameAlreadyExistsException();
    }

    // 비밀번호 유효성 검증
    isValidPasswordFormat(request.password());

    User user = authConverter.fromUserSignUpRequestToUser(request,
        bCryptPasswordEncoder.encode(request.password()), role);

    userRepository.save(user);

    return authConverter.fromUserToUserSignUpResponse(user);
  }


  private void isValidPasswordFormat(String password) {
    if (password == null || !password
        .matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,30}$")) {
      throw new InvalidPasswordFormatException();
    }
  }


  public void isValidEmailFormat(@NotBlank String email) {
    if (email == null || !(email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$"))) {
      throw new InvalidEmailFormatException();
    }
  }

  public boolean existsByEmail(@NotBlank String email) {
    if (userRepository.existsByEmail(email)) {
      return true; // 이미 사용 중인 이메일
    }
    return false;
  }

  public void isValidNicknameFormat(@NotBlank String nickname) {
    if (nickname == null || !nickname.matches("^[가-힣a-zA-Z0-9]{2,20}$")) {
      throw new InvalidNicknameFormatException();
    }
  }

  public boolean existsByNickname(@NotBlank String nickname) {
    if (userRepository.existsByNickname(nickname)) {
      return true; // 이미 사용 중인 닉네임
    }
    return false;
  }
}
