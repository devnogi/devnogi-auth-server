package until.the.eternity.das.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Getter
@RequiredArgsConstructor
public enum GlobalExceptionCode implements ExceptionCode {
  SERVER_ERROR(INTERNAL_SERVER_ERROR, "서버 내부 에러입니다."),

  // JWT TOKEN
  INVALID_TOKEN(UNAUTHORIZED, "유효하지 않는 토큰입니다."),
  EXPIRED_TOKEN(UNAUTHORIZED, "만료된 토큰입니다."),

  // AUTH
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입되어 있는 이메일입니다."),
  NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 닉네임입니다."),
  INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "유효하지 않은 이메일 형식입니다."),
  INVALID_NICKNAME_FORMAT(HttpStatus.BAD_REQUEST,
    "유효하지 않은 닉네임 형식입니다. 닉네임은 2자 이상 20자 이하이면서 영문, 숫자, 한글만 사용 가능합니다."),
  INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST,
    "유효하지 않은 비밀번호 형식입니다. 비밀번호는 8자 이상 20자 이하이면서 1개 이상의 알파벳, 숫자, 특수문자로 구성되어야 합니다."),
  USER_NOT_EXISTS(HttpStatus.BAD_REQUEST, "존재하지 않는 회원입니다"),
  USER_ROLE_NOT_EXISTS(HttpStatus.BAD_REQUEST, "USER Role이 없습니다."),
  ADMIN_ROLE_NOT_EXISTS(HttpStatus.BAD_REQUEST, "ADMIN Role이 DB에 존재하지 않습니다."),
  INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "잘못된 비밀번호 입니다."),

  // OAUTH
  NOT_SUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜로그인입니다");

  private final HttpStatus status;
  private final String message;

  @Override
  public String getCode() {
    return this.name();
  }
}
