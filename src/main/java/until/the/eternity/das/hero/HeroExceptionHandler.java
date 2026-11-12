package until.the.eternity.das.hero;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import until.the.eternity.das.common.response.CommonResponse;

@Order(-1)
@RestControllerAdvice(assignableTypes = HeroController.class)
public class HeroExceptionHandler {
    @ExceptionHandler({
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class
    })
    public ResponseEntity<CommonResponse<Void>> invalidInput(Exception ignored) {
        return ResponseEntity.badRequest()
                .body(CommonResponse.error("HERO_INVALID", "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(HeroException.class)
    public ResponseEntity<CommonResponse<Void>> handle(HeroException e) {
        return ResponseEntity.status(e.status())
                .body(CommonResponse.error(e.code(), e.getMessage()));
    }
}
