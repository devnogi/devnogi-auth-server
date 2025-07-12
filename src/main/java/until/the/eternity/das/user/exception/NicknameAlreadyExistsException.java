package until.the.eternity.das.user.exception;

import static until.the.eternity.das.user.exception.UserExceptionCode.NICKNAME_ALREADY_EXISTS;

import until.the.eternity.das.common.exception.CustomException;

public class NicknameAlreadyExistsException extends CustomException {

  public NicknameAlreadyExistsException() {
    super(NICKNAME_ALREADY_EXISTS);
  }

}
