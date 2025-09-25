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

  private String secretKey;
  private Long accessTokenValidity;
  private Long refreshTokenValidity;

  private final String ISSUER = "devnogi-auth-server";

  private final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

  private final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
}
