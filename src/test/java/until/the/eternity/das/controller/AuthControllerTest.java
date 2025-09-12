package until.the.eternity.das.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import until.the.eternity.das.dto.request.LoginRequest;
import until.the.eternity.das.dto.response.TokenResponse;
import until.the.eternity.das.handler.GlobalExceptionHandler;
import until.the.eternity.das.service.AuthService;
import until.the.eternity.das.service.CustomUserDetailsService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import until.the.eternity.das.config.SecurityConfig;

@WebMvcTest(controllers = AuthController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SuppressWarnings("removal")
    @MockBean
    private AuthService authService;

    @SuppressWarnings("removal")
    @MockBean
    private CustomUserDetailsService customUserDetailsService; // SecurityConfig가 의존하므로 모킹 필요

    @Test
    @DisplayName("로그인 성공 시 JWT 토큰을 반환한다")
    void givenValidCredentials_whenLogin_thenReturnsTokens() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest();
        // reflection을 사용하거나, setter를 추가하여 테스트용 객체 생성
        setField(loginRequest, "email", "test@example.com");
        setField(loginRequest, "password", "password123!");

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("fake-access-token")
                .refreshToken("fake-refresh-token")
                .build();

        given(authService.login(any(LoginRequest.class))).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("fake-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("fake-refresh-token"));
    }

    @Test
    @DisplayName("잘못된 자격 증명으로 로그인 시 401 Unauthorized를 반환한다")
    void givenInvalidCredentials_whenLogin_thenReturnsUnauthorized() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest();
        setField(loginRequest, "email", "test@example.com");
        setField(loginRequest, "password", "wrong-password");

        given(authService.login(any(LoginRequest.class))).willThrow(new BadCredentialsException("Invalid credentials"));

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("유효하지 않은 형식의 이메일로 로그인 시 400 Bad Request를 반환한다")
    void givenInvalidEmailFormat_whenLogin_thenReturnsBadRequest() throws Exception {
        // given
        LoginRequest loginRequest = new LoginRequest();
        setField(loginRequest, "email", "not-an-email");
        setField(loginRequest, "password", "password123!");

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    // LoginRequest에 setter가 없으므로 reflection으로 값 설정
    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
