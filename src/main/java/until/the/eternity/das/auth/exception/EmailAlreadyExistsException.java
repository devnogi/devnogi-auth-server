package until.the.eternity.das.auth.exception;

import static until.the.eternity.das.auth.exception.AuthExceptionCode.EMAIL_ALREADY_EXISTS;

import until.the.eternity.das.common.exception.CustomException;

public class EmailAlreadyExistsException extends CustomException {

  public EmailAlreadyExistsException() {
    super(EMAIL_ALREADY_EXISTS);
  }

}
