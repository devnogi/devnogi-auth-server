package until.the.eternity.das.common.constant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConstant {

    // Spring binds these properties and Lombok generates their accessors.
    @SuppressWarnings("PMD.UnusedPrivateField")
    private String secretKey;

    @SuppressWarnings("PMD.UnusedPrivateField")
    private Long accessTokenValidity;

    @SuppressWarnings("PMD.UnusedPrivateField")
    private Long refreshTokenValidity;

    @SuppressWarnings("PMD.UnusedPrivateField")
    private String issuer;

    @SuppressWarnings("PMD.UnusedPrivateField")
    private String accessTokenCookieName;

    @SuppressWarnings("PMD.UnusedPrivateField")
    private String refreshTokenCookieName;

    @SuppressWarnings("PMD.UnusedPrivateField")
    private String cookieDomain = ".memonogi.com";

    @SuppressWarnings("PMD.UnusedPrivateField")
    private boolean cookieSecure = true;

    @SuppressWarnings("PMD.UnusedPrivateField")
    private String cookieSameSite = "None";
}
