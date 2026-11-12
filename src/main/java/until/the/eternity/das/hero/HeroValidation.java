package until.the.eternity.das.hero;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

public final class HeroValidation {
    private HeroValidation() {}

    public static String link(String value) {
        if (value == null || value.isEmpty()) return null;
        if (value.length() > 2048 || value.chars().anyMatch(c -> c <= 32 || c == 127 || c == '\\'))
            throw HeroException.invalid("링크 형식이 올바르지 않습니다.");
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443))
                throw HeroException.invalid("HTTPS 메모노기 하위 도메인 링크만 사용할 수 있습니다.");
            host = host.toLowerCase(Locale.ROOT);
            if (!host.endsWith(".memonogi.com")
                    || host.length() > 253
                    || !host.matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+memonogi\\.com"))
                throw HeroException.invalid("메모노기 하위 도메인 링크만 사용할 수 있습니다.");
            return uri.toASCIIString();
        } catch (java.net.URISyntaxException e) {
            throw HeroException.invalid("링크 형식이 올바르지 않습니다.");
        }
    }

    public static String text(String value) {
        if (value == null || value.isBlank() || value.length() > 200)
            throw HeroException.invalid("이미지 설명은 1~200자여야 합니다.");
        return value.strip();
    }

    public static void period(Instant start, Instant end) {
        if (start != null && end != null && !start.isBefore(end))
            throw HeroException.invalid("종료 시각은 시작 시각보다 늦어야 합니다.");
    }

    public static String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw HeroException.invalid("처리 사유는 1~500자여야 합니다.");
        return reason.strip();
    }
}
