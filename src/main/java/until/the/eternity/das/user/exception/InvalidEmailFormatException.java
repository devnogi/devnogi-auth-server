package until.the.eternity.das.user.exception;

import static until.the.eternity.das.user.exception.UserExceptionCode.INVALID_EMAIL_FORMAT;

import until.the.eternity.das.common.exception.CustomException;

public class InvalidEmailFormatException extends CustomException {

  public InvalidEmailFormatException() {
    super(INVALID_EMAIL_FORMAT);
  }

}
