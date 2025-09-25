package until.the.eternity.das.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import until.the.eternity.das.common.response.CommonResponse;

import static until.the.eternity.das.common.exception.GlobalExceptionCode.SERVER_ERROR;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(CustomException.class)
  protected ResponseEntity<CommonResponse<?>> handleCustomException(CustomException exception) {
    log.error("Caught CustomException: {}", exception.getMessage(), exception);
    ExceptionResponse exResponse = ExceptionResponse.from(exception);
    CommonResponse<?> response = CommonResponse.error(exResponse.code(), exResponse.message());
    return ResponseEntity.status(exResponse.status())
      .body(response);
  }

  @ExceptionHandler(Exception.class)
  protected ResponseEntity<CommonResponse<?>> handleException(Exception exception) {
    ExceptionResponse exResponse = ExceptionResponse.from(SERVER_ERROR);
    CommonResponse<?> response = CommonResponse.error(exResponse.code(), exResponse.message());
    return ResponseEntity.status(exResponse.status())
      .body(response);
  }
}

