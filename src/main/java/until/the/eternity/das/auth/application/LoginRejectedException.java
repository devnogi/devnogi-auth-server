package until.the.eternity.das.auth.application;

import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;

/** Expected login rejections whose failure records must be committed. */
public final class LoginRejectedException extends CustomException {
  private LoginRejectedException(GlobalExceptionCode code) {
    super(code);
  }

  static LoginRejectedException invalidPassword() {
    return new LoginRejectedException(GlobalExceptionCode.INVALID_PASSWORD);
  }

  static LoginRejectedException accountLocked() {
    return new LoginRejectedException(GlobalExceptionCode.ACCOUNT_LOCKED);
  }
}
