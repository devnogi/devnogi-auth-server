package until.the.eternity.das.common.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class CommonResponseAdvice implements ResponseBodyAdvice<Object> {

  private final ObjectMapper objectMapper;

  @Override
  public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return !returnType.getParameterType()
      .equals(Void.TYPE)
      && !CommonResponse.class.isAssignableFrom(returnType.getParameterType());
  }

  @Override
  public Object beforeBodyWrite(Object body,
                                MethodParameter returnType,
                                MediaType selectedContentType,
                                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                org.springframework.http.server.ServerHttpRequest request,
                                org.springframework.http.server.ServerHttpResponse response) {

    try {
      if (request instanceof ServletServerHttpRequest servletRequest) {
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String path = httpRequest.getRequestURI();

        if (isSwaggerPath(path)) {
          return body;
        }
      }

      if (body instanceof CommonResponse) {
        return body;
      }

      return CommonResponse.success(body);
    } catch (Exception e) {
      return body;
    }
  }

  private boolean isSwaggerPath(String path) {
    return path != null && (
      path.startsWith("/v3/api-docs") ||
        path.startsWith("/swagger-ui") ||
        path.startsWith("/swagger-resources")
    );
  }
}