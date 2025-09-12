package until.the.eternity.das.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import until.the.eternity.das.oauth.entity.OauthUser;

import java.util.Optional;

public interface OAuthUserRepository extends JpaRepository<OauthUser, Long> {
    Optional<OauthUser> findByProviderAndProviderUserId(String provider, String providerUserId);
}
