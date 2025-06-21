package until.the.eternity.dcs.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예외처리기 기능 테스트를 위한 데모 컨트롤러 입니다.
 */
@RestController
@RequestMapping(value = "/api/demo")
public class DemoController {

	@GetMapping("/success")
	public DemoDto success() {
		return DemoDto.builder()
				.response("success")
				.build();
	}

	@GetMapping("/fail")
	public ResponseEntity<String> fail() {
		throw new DemoException();
	}

	@GetMapping("/server-error")
	public ResponseEntity<String> server() {
		throw new RuntimeException("server error");
	}
}
