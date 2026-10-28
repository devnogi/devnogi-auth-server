package until.the.eternity.das.oauth.dto;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** 소셜 로그인 결과를 담는 DTO (SecurityContext에 올라가는 Principal) */
@Getter
public class OauthUserDTO implements OAuth2User {

    private final String providerUserId;
    private final String email;
    private final String provider;

    public OauthUserDTO(String providerUserId, String email, String provider) {
        this.providerUserId = providerUserId;
        this.email = email;
        this.provider = provider;
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("providerUserId", providerUserId);
        attributes.put("email", email);
        attributes.put("provider", provider);
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getName() {
        return providerUserId;
    }
}
