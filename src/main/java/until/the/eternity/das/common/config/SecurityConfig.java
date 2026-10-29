package until.the.eternity.das.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import until.the.eternity.das.common.filter.UserAuthenticationFilter;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.response.CommonResponse;
import until.the.eternity.das.oauth.handler.OAuth2SuccessHandler;
import until.the.eternity.das.oauth.service.CustomOAuth2UserService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final CustomOAuth2UserService customOAuth2UserService;
  private final OAuth2SuccessHandler oAuth2SuccessHandler;
  private final UserAuthenticationFilter userAuthenticationFilter;
  private final ObjectMapper objectMapper;

  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http.csrf(csrf -> csrf.disable());

    http.formLogin(form -> form.disable());

    http.httpBasic(basic -> basic.disable());

    http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
      (request, response, exception) -> {
        GlobalExceptionCode error = GlobalExceptionCode.INVALID_TOKEN;
        response.setStatus(error.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), CommonResponse.error(error.getCode(), error.getMessage()));
      },
      request -> request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/")
    ));

    http.authorizeHttpRequests(auth -> auth
      .requestMatchers("/actuator/health")
      .permitAll()
      .requestMatchers("/swagger", "/swagger-ui.html", "/swagger-ui/**",
        "/api-docs", "/api-docs/**", "/v3/api-docs/**")
      .permitAll()
      .requestMatchers("/api/auth/admin/signup")
      .hasRole("SUPER_ADMIN")
      .requestMatchers("/api/auth/**")
      .permitAll()
      .requestMatchers("/api/user/verification/public/**")
      .permitAll()
      .requestMatchers("/api/admin/")
      .hasAnyRole("ADMIN", "SUPER_ADMIN")

      .requestMatchers("/oauth2/**", "/login/oauth2/**")
      .permitAll()

      .anyRequest()
      .authenticated()
    );

    http.sessionManagement(session ->
      session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
    );

    http.oauth2Login(oauth2 -> oauth2
      .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
      .successHandler(oAuth2SuccessHandler)
    );

    http.addFilterBefore(
      userAuthenticationFilter,
      UsernamePasswordAuthenticationFilter.class
    );

    return http.build();
  }
}
