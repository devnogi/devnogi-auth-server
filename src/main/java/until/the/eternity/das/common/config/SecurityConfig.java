package until.the.eternity.das.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import until.the.eternity.das.common.filter.UserAuthenticationFilter;
import until.the.eternity.das.oauth.handler.OAuth2SuccessHandler;
import until.the.eternity.das.oauth.service.CustomOAuth2UserService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final CustomOAuth2UserService customOAuth2UserService; // ✅ 소셜 로그인 사용자 매핑
  private final OAuth2SuccessHandler oAuth2SuccessHandler;       // ✅ 소셜 로그인 성공 핸들러
  private final UserAuthenticationFilter userAuthenticationFilter;

  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    // CSRF 비활성화
    http.csrf(csrf -> csrf.disable());

    // Form 로그인 방식 비활성화 (우리는 REST API 기반)
    http.formLogin(form -> form.disable());

    // HTTP Basic 인증 방식 비활성화
    http.httpBasic(basic -> basic.disable());

    // 경로별 인가 정책
    http.authorizeHttpRequests(auth -> auth
      .requestMatchers("/swagger", "/swagger-ui.html", "/swagger-ui/**",
        "/api-docs", "/api-docs/**", "/v3/api-docs/**")
      .permitAll()
      .requestMatchers("/api/auth/admin/signup")
      .hasRole("SUPER_ADMIN") // Todo: Role이 String으로 되어 있어서 추후 수정 필요
      .requestMatchers("/api/auth/**")
      .permitAll()
      .requestMatchers("/api/admin/")
      .hasRole("ADMIN")

      // ✅ 소셜 로그인 관련 경로 허용
      .requestMatchers("/oauth2/**", "/login/oauth2/**")
      .permitAll()

      .anyRequest()
      .authenticated()
    );

    // 세션을 STATELESS로 설정 (JWT 기반 인증 사용)
    http.sessionManagement(session ->
      session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    );

    // ✅ 소셜 로그인 설정 추가
    http.oauth2Login(oauth2 -> oauth2
      .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService)) // provider에서 내려준 attributes 처리
      .successHandler(oAuth2SuccessHandler) // 로그인 성공 시 JWT 발급 및 후처리
    );

    http.addFilterBefore(
      userAuthenticationFilter,
      UsernamePasswordAuthenticationFilter.class
    );

    return http.build();
  }
}
