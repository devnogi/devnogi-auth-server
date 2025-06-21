package until.the.eternity.common.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final String timestamp;

    @Builder
    private ApiResponse(boolean success, String code, String message, T data, String timestamp) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("COMMON_SUCCESS") // TODO: 아예 enum으로 관리하는 건 고민해보자
                .message("요청이 성공적으로 처리되었습니다.")
                .data(data)
                .timestamp(Instant.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .timestamp(Instant.now().toString())
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(null)
                .timestamp(Instant.now().toString())
                .build();
    }
}
