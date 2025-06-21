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
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // void나 ApiResponse 타입이면 감싸지 않음
        return !returnType.getParameterType().equals(Void.TYPE)
                && !ApiResponse.class.isAssignableFrom(returnType.getParameterType());
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

                // Swagger 관련 요청은 감싸지 않음
                if (isSwaggerPath(path)) {
                    return body;
                }
            }

            // 이미 ApiResponse 타입이면 다시 감싸지 않음
            if (body instanceof ApiResponse) {
                return body;
            }

            return ApiResponse.success(body);
        } catch (Exception e) {
            // Swagger에서 500 방지를 위한 fallback 처리
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