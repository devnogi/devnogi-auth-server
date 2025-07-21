package until.the.eternity.das.auth.exception;

import static until.the.eternity.das.auth.exception.AuthExceptionCode.INVALID_NICKNAME_FORMAT;

import until.the.eternity.das.common.exception.CustomException;

public class InvalidNicknameFormatException extends CustomException {

  public InvalidNicknameFormatException() {
    super(INVALID_NICKNAME_FORMAT);
  }

}
