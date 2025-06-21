package until.the.eternity.das.demo.controller;

import until.the.eternity.das.common.exception.CustomException;

import static until.the.eternity.das.demo.controller.DemoDomainExceptionCode.DEMO_DOMAIN_NOT_FOUND_EXCEPTION;

/**
 * 예시 Exception 입니다.
 * CustomException을 상속받아 구현합니다.
 */
public class DemoException extends CustomException {
	public DemoException() {
		super(DEMO_DOMAIN_NOT_FOUND_EXCEPTION);
	}
}
