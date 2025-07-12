package until.the.eternity.das.user.application;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import until.the.eternity.das.user.dto.request.SignUpRequest;
import until.the.eternity.das.user.dto.response.SignUpResponse;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.exception.EmailAlreadyExistsException;
import until.the.eternity.das.user.exception.InvalidEmailFormatException;
import until.the.eternity.das.user.exception.InvalidNicknameFormatException;
import until.the.eternity.das.user.exception.InvalidPasswordFormatException;
import until.the.eternity.das.user.exception.NicknameAlreadyExistsException;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final UserConverter userConverter;
  private final BCryptPasswordEncoder bCryptPasswordEncoder;


  @Transactional
  public SignUpResponse signUp(SignUpRequest request) {

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

    User user = userConverter.fromUserSignUpRequestToUser(request,
        bCryptPasswordEncoder.encode(request.password()));
    return userConverter.fromUserToUserSignUpResponse(userRepository.save(user));
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
