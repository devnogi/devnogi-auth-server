package until.the.eternity.dcs.demo.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import until.the.eternity.dcs.common.exception.ExceptionCode;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 예시 ExceptionCode 입니다.
 * ExceptionCode를 상속받아 구현합니다.
 */
@Getter
@RequiredArgsConstructor
public enum DemoDomainExceptionCode implements ExceptionCode {
	DEMO_DOMAIN_NOT_FOUND_EXCEPTION(NOT_FOUND, "해당 데모 도메인을 찾을 수 없습니다"),
	;

	private final HttpStatus status;
	private final String message;

	@Override
	public String getCode() {
		return this.name();
	}
}
