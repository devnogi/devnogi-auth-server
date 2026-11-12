package until.the.eternity.das.hero;

import org.springframework.http.HttpStatus;

public class HeroException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public HeroException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = "HERO_" + code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static HeroException invalid(String message) {
        return new HeroException(HttpStatus.BAD_REQUEST, "INVALID", message);
    }

    public static HeroException conflict(String message) {
        return new HeroException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static HeroException forbidden(String message) {
        return new HeroException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static HeroException missing() {
        return new HeroException(HttpStatus.NOT_FOUND, "NOT_FOUND", "항목을 찾을 수 없습니다.");
    }
}
