package until.the.eternity.das.common.aop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.Status;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ActiveUserAspect {

  private final UserRepository userRepository;

  @Before("@annotation(ActiveUserRequired)")
  public void checkUserStatus() {
    Authentication authentication = SecurityContextHolder.getContext()
      .getAuthentication();

    Long userId;

    try {
      userId = (Long) authentication.getPrincipal();
    } catch (Exception e) {
      throw new CustomException(GlobalExceptionCode.INVALID_TOKEN);
    }

    User user = userRepository.findById(userId)
      .orElseThrow(() -> new CustomException(GlobalExceptionCode.USER_NOT_EXISTS));

    Status status = user.getStatus();

    if (status != Status.ACTIVE) {
      throw new CustomException(GlobalExceptionCode.USER_NOT_EXISTS);
    }
  }
}
