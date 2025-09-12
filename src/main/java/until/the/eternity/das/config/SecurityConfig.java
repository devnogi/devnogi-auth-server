package until.the.eternity.das.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.AntPathMatcher; // Import AntPathMatcher
import jakarta.servlet.http.HttpServletRequest; // Import HttpServletRequest

import until.the.eternity.das.handler.OAuth2AuthenticationSuccessHandler;
import until.the.eternity.das.repository.UserRepository;
import until.the.eternity.das.repository.OAuthUserRepository;
import until.the.eternity.das.service.CustomOAuth2UserService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final UserRepository userRepository;
    private final OAuthUserRepository oAuthUserRepository;

    private static final String[] SWAGGER_URL_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources",
            "/swagger-resources/**",
            "/webjars/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(SWAGGER_URL_PATHS).permitAll()
                        .requestMatchers("/api/auth/**", "/oauth2/**").permitAll()
                        .anyRequest().authenticated()
                );

        http
                .securityMatcher(request -> !isSwaggerPath(request)) // Custom matcher to exclude Swagger paths
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> 
                            userInfo.userService(new CustomOAuth2UserService(userRepository, oAuthUserRepository, passwordEncoder())))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                );

        return http.build();
    }

    private boolean isSwaggerPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        AntPathMatcher pathMatcher = new AntPathMatcher();
        for (String swaggerPath : SWAGGER_URL_PATHS) {
            if (pathMatcher.match(swaggerPath, path)) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
