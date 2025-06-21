package until.the.eternity.das.common.exception;

import lombok.Builder;
import org.springframework.http.HttpStatus;

@Builder
public record ExceptionResponse(
	HttpStatus status,
	String code,
	String message
) {

	public static ExceptionResponse from(CustomException exception) {
		return ExceptionResponse.builder()
			.status(exception.getCode().getStatus())
			.code(exception.getCode().getCode())
			.message(exception.getCode().getMessage())
			.build();
	}

	public static ExceptionResponse from(ExceptionCode code) {
		return ExceptionResponse.builder()
			.status(code.getStatus())
			.code(code.getCode())
			.message(code.getMessage())
			.build();
	}
}
