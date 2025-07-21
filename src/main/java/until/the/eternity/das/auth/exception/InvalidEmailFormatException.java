package until.the.eternity.das.auth.exception;

import static until.the.eternity.das.auth.exception.AuthExceptionCode.INVALID_EMAIL_FORMAT;

import until.the.eternity.das.common.exception.CustomException;

public class InvalidEmailFormatException extends CustomException {

  public InvalidEmailFormatException() {
    super(INVALID_EMAIL_FORMAT);
  }

}
