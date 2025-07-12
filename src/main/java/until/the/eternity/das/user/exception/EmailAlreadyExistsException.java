package until.the.eternity.das.user.exception;

import static until.the.eternity.das.user.exception.UserExceptionCode.EMAIL_ALREADY_EXISTS;

import until.the.eternity.das.common.exception.CustomException;

public class EmailAlreadyExistsException extends CustomException {

  public EmailAlreadyExistsException() {
    super(EMAIL_ALREADY_EXISTS);
  }

}
