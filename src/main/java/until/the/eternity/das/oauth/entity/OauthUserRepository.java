package until.the.eternity.das.oauth.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import until.the.eternity.das.user.entity.User;

import java.util.Optional;

public interface OauthUserRepository extends JpaRepository<OauthUser, Long> {
  Optional<OauthUser> findByUser(User user);

  @Query("SELECT o FROM OauthUser o JOIN FETCH o.user u JOIN FETCH u.role WHERE o.provider = :provider AND o.providerUserId = :providerUserId")
  Optional<OauthUser> findByProviderAndProviderUserIdWithUserAndRoles(@Param("provider") String provider,
                                                                      @Param("providerUserId") String providerUserId);
}
