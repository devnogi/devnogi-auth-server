package until.the.eternity.das.user.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import until.the.eternity.das.common.exception.ExceptionCode;


@Getter
@RequiredArgsConstructor
public enum UserExceptionCode implements ExceptionCode {
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입되어 있는 이메일입니다."),
  NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 닉네임입니다."),
  INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "유효하지 않은 이메일 형식입니다."),
  INVALID_NICKNAME_FORMAT(HttpStatus.BAD_REQUEST,
      "유효하지 않은 닉네임 형식입니다. 닉네임은 2자 이상 20자 이하의 영문, 숫자, 한글로 구성되어야 합니다."),
  INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST,
      "유효하지 않은 비밀번호 형식입니다. 비밀번호는 8자 이상 20자 이하의 영문, 숫자, 특수문자로 구성되어야 합니다.");


  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return this.name();
  }
}

