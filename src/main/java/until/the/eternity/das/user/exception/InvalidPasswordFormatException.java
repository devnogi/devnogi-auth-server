package until.the.eternity.das.user.exception;

import static until.the.eternity.das.user.exception.UserExceptionCode.INVALID_PASSWORD_FORMAT;

import until.the.eternity.das.common.exception.CustomException;

public class InvalidPasswordFormatException extends CustomException {

  public InvalidPasswordFormatException() {
    super(INVALID_PASSWORD_FORMAT);
  }

}
