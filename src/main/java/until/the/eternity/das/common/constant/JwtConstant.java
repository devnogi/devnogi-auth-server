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
  private String issuer;
  private String accessTokenCookieName;
  private String refreshTokenCookieName;

}
