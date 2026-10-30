package until.the.eternity.das.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;
import until.the.eternity.das.auth.application.AuthService;
import until.the.eternity.das.auth.application.NicknameCandidateService;
import until.the.eternity.das.auth.dto.request.LoginRequest;
import until.the.eternity.das.auth.dto.response.LoginResultResponse;
import until.the.eternity.das.auth.presentation.AuthController;
import until.the.eternity.das.common.config.SecurityConfig;
import until.the.eternity.das.common.constant.JwtConstant;
import until.the.eternity.das.common.exception.CustomException;
import until.the.eternity.das.common.exception.GlobalExceptionCode;
import until.the.eternity.das.common.filter.UserAuthenticationFilter;
import until.the.eternity.das.common.util.CookieUtil;
import until.the.eternity.das.common.util.JwtUtil;
import until.the.eternity.das.oauth.handler.OAuth2SuccessHandler;
import until.the.eternity.das.oauth.service.CustomOAuth2UserService;
import until.the.eternity.das.oauth.service.OAuthExchangeService;
import until.the.eternity.das.oauth.service.SocialAuthService;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.token.application.TokenService;
import until.the.eternity.das.user.application.UserService;
import until.the.eternity.das.user.dto.response.UserInfoResponse;
import until.the.eternity.das.user.entity.User;
import until.the.eternity.das.user.entity.UserRepository;
import until.the.eternity.das.user.entity.enums.Status;
import until.the.eternity.das.user.presentation.UserController;

@WebMvcTest(
        controllers = {
            AuthController.class,
            UserController.class,
            EmailLoginContractTest.HealthController.class
        },
        properties = {"jwt.cookie-domain=", "jwt.cookie-secure=false", "jwt.cookie-same-site=Lax"})
@Import({
    SecurityConfig.class,
    UserAuthenticationFilter.class,
    CookieUtil.class,
    JwtUtil.class,
    EmailLoginContractTest.Configuration.class,
    EmailLoginContractTest.HealthController.class
})
class EmailLoginContractTest {
    @RestController
    static class HealthController {
        @GetMapping("/actuator/health")
        java.util.Map<String, String> health() {
            return java.util.Map.of("status", "UP");
        }
    }

    @TestConfiguration
    @EnableConfigurationProperties(JwtConstant.class)
    static class Configuration {}

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtConstant settings;
    @Autowired private JwtUtil jwt;
    @MockitoBean private AuthService authService;
    @MockitoBean private TokenService tokenService;
    @MockitoBean private SocialAuthService socialAuthService;
    @MockitoBean private OAuthExchangeService oauthExchangeService;
    @MockitoBean private UserService userService;
    @MockitoBean private NicknameCandidateService nicknameCandidateService;
    @MockitoBean private UserRepository users;
    @MockitoBean private CustomOAuth2UserService oauthUsers;
    @MockitoBean private OAuth2SuccessHandler oauthSuccess;
    private JsonNode contract;
    private User user;

    @BeforeEach
    void fixtures() throws Exception {
        try (var input = Files.newInputStream(Path.of("contracts/openapi.yaml"))) {
            contract = mapper.valueToTree(new Yaml().load(input));
        }
        user =
                User.builder()
                        .id(1L)
                        .email("local@example.test")
                        .nickname("localuser")
                        .status(Status.ACTIVE)
                        .role(Role.builder().name(Name.USER).build())
                        .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                        .build();
        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test
    void loginReturnsContractUserAndTwoSeparateHostOnlyCookies() throws Exception {
        when(authService.login(any(), anyString(), any()))
                .thenReturn(new LoginResultResponse(user, "synthetic-access", "synthetic-refresh"));
        JsonNode request = mapper.valueToTree(new LoginRequest(user.getEmail(), "LocalPass123!"));
        assertSchema(request, contract.at("/components/schemas/LoginRequest"));
        MvcResult result =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(mapper.writeValueAsBytes(request)))
                        .andExpect(status().isOk())
                        .andReturn();
        assertResponse(result, "/api/auth/login", "post");
        assertLocalCookies(result, false);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("synthetic-access", "synthetic-refresh", "LocalPass123!");
    }

    @Test
    void loginErrorsKeepExistingStatusCodesAndNeverIssueCookies() throws Exception {
        for (GlobalExceptionCode code :
                new GlobalExceptionCode[] {
                    GlobalExceptionCode.USER_NOT_EXISTS,
                    GlobalExceptionCode.INVALID_PASSWORD,
                    GlobalExceptionCode.ACCOUNT_LOCKED
                }) {
            doThrow(new CustomException(code)).when(authService).login(any(), anyString(), any());
            MvcResult result =
                    mvc.perform(
                                    post("/api/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(
                                                    "{\"email\":\"local@example.test\",\"password\":\"wrong\"}"))
                            .andExpect(status().is(code.getStatus().value()))
                            .andExpect(jsonPath("$.code").value(code.getCode()))
                            .andReturn();
            assertResponse(result, "/api/auth/login", "post");
            assertThat(result.getResponse().getHeaders("Set-Cookie")).isEmpty();
        }
    }

    @Test
    void healthIsPublicWhileUserInfoRemainsProtected() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/user/info")).andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserUsesAccessCookieAndMatchesContract() throws Exception {
        when(userService.getUserInfo(1L)).thenReturn(UserInfoResponse.of(user));
        MvcResult result =
                mvc.perform(get("/api/user/info").cookie(accessCookie()))
                        .andExpect(status().isOk())
                        .andReturn();
        assertResponse(result, "/api/user/info", "get");
        verify(userService).getUserInfo(1L);
    }

    @Test
    void missingMalformedAndExpiredAccessAlwaysReturnJson401EvenForHtmlAccept() throws Exception {
        String expired =
                Jwts.builder()
                        .subject(user.getEmail())
                        .claim("userId", 1L)
                        .claim("role", "USER")
                        .claim("type", "ACCESS")
                        .expiration(new Date(1000))
                        .signWith(
                                Keys.hmacShaKeyFor(Decoders.BASE64.decode(settings.getSecretKey())))
                        .compact();
        for (Cookie[] cookies :
                new Cookie[][] {
                    {},
                    {new Cookie("access_token", "malformed")},
                    {new Cookie("access_token", expired)}
                }) {
            var request = get("/api/user/info").accept(MediaType.TEXT_HTML);
            if (cookies.length > 0) request.cookie(cookies);
            MvcResult result =
                    mvc.perform(request)
                            .andExpect(status().isUnauthorized())
                            .andExpect(
                                    content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                            .andExpect(header().doesNotExist("Location"))
                            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                            .andReturn();
            assertResponse(result, "/api/user/info", "get");
        }
        verifyNoInteractions(userService);
    }

    @Test
    void refreshWorksWithoutAccessCookieAndPreservesFailureStatus() throws Exception {
        when(tokenService.refresh("synthetic-refresh"))
                .thenReturn(new LoginResultResponse(user, "new-access", "new-refresh"));
        MvcResult success =
                mvc.perform(
                                post("/api/auth/refresh")
                                        .cookie(new Cookie("refresh_token", "synthetic-refresh")))
                        .andExpect(status().isOk())
                        .andReturn();
        assertResponse(success, "/api/auth/refresh", "post");
        assertLocalCookies(success, false);
        MvcResult missing =
                mvc.perform(post("/api/auth/refresh"))
                        .andExpect(status().isUnauthorized())
                        .andReturn();
        assertResponse(missing, "/api/auth/refresh", "post");
        when(tokenService.refresh("revoked"))
                .thenThrow(new CustomException(GlobalExceptionCode.INVALID_REFRESH_TOKEN));
        MvcResult revoked =
                mvc.perform(
                                post("/api/auth/refresh")
                                        .cookie(new Cookie("refresh_token", "revoked")))
                        .andExpect(status().isUnauthorized())
                        .andReturn();
        assertResponse(revoked, "/api/auth/refresh", "post");
    }

    @Test
    void authenticatedLogoutRevokesServerSessionAndExpiresMatchingCookies() throws Exception {
        MvcResult result =
                mvc.perform(post("/api/auth/logout").cookie(accessCookie()))
                        .andExpect(status().isOk())
                        .andReturn();
        assertResponse(result, "/api/auth/logout", "post");
        assertLocalCookies(result, true);
        verify(authService).logout(1L);
    }

    @Test
    void realJwtCookiesForOrdinaryAccountFitAndKeepScopeThroughRefreshAndLogout() throws Exception {
        String firstAccess = jwt.generateAccessToken(user);
        String firstRefresh = jwt.generateRefreshToken(user);
        String nextAccess = jwt.generateAccessToken(user);
        String nextRefresh = jwt.generateRefreshToken(user);
        assertThat(jwt.extractAllClaims(firstRefresh).getId()).isNotBlank();
        assertThat(nextRefresh).isNotEqualTo(firstRefresh);
        when(authService.login(any(), anyString(), any()))
                .thenReturn(new LoginResultResponse(user, firstAccess, firstRefresh));
        when(tokenService.refresh(firstRefresh))
                .thenReturn(new LoginResultResponse(user, nextAccess, nextRefresh));

        MvcResult login =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsBytes(
                                                        new LoginRequest(
                                                                user.getEmail(), "LocalPass123!"))))
                        .andExpect(status().isOk())
                        .andReturn();
        assertLocalCookies(login, false);
        assertCookieFits(login, "access_token", firstAccess);
        assertCookieFits(login, "refresh_token", firstRefresh);

        MvcResult refresh =
                mvc.perform(
                                post("/api/auth/refresh")
                                        .cookie(new Cookie("refresh_token", firstRefresh)))
                        .andExpect(status().isOk())
                        .andReturn();
        assertLocalCookies(refresh, false);
        assertCookieFits(refresh, "access_token", nextAccess);
        assertCookieFits(refresh, "refresh_token", nextRefresh);
        verify(tokenService).refresh(firstRefresh);

        MvcResult logout =
                mvc.perform(post("/api/auth/logout").cookie(new Cookie("access_token", nextAccess)))
                        .andExpect(status().isOk())
                        .andReturn();
        assertLocalCookies(logout, true);
        verify(authService).logout(1L);
    }

    private void assertCookieFits(MvcResult result, String name, String token) {
        String header =
                result.getResponse().getHeaders("Set-Cookie").stream()
                        .filter(value -> value.startsWith(name + "="))
                        .findFirst()
                        .orElseThrow();
        assertThat(header.split(";", 2)[0]).isEqualTo(name + "=" + token);
        assertThat((name + token).getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(4096);
    }

    @Test
    void anonymousLogoutOnlyClearsCookies() throws Exception {
        MvcResult result =
                mvc.perform(post("/api/auth/logout")).andExpect(status().isOk()).andReturn();
        assertResponse(result, "/api/auth/logout", "post");
        assertLocalCookies(result, true);
        verify(authService, never()).logout(any());
    }

    @Test
    void oauthBrowserRedirectsAndExistingAdminRoleRestrictionRemain() throws Exception {
        mvc.perform(get("/oauth2/authorization/test"))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        header().string(
                                        "Location",
                                        org.hamcrest.Matchers.startsWith(
                                                "http://127.0.0.1:19001/oauth/authorize")));
        mvc.perform(get("/login/oauth2/code/test").param("error", "access_denied"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/api/auth/admin/signup")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/admin/signup").cookie(accessCookie()))
                .andExpect(status().isForbidden());
        verify(authService, never()).signUpAdmin(any());
    }

    private Cookie accessCookie() {
        return new Cookie("access_token", jwt.generateAccessToken(user));
    }

    private void assertLocalCookies(MvcResult result, boolean deleted) {
        var cookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(cookies)
                .hasSize(2)
                .allSatisfy(
                        cookie -> {
                            assertThat(cookie)
                                    .contains("HttpOnly", "Path=/", "SameSite=Lax")
                                    .doesNotContain("Domain=", "Secure");
                            if (deleted) assertThat(cookie).contains("Max-Age=0");
                        });
        assertThat(cookies.get(0)).startsWith("access_token=");
        assertThat(cookies.get(1)).startsWith("refresh_token=");
    }

    private void assertResponse(MvcResult result, String path, String method) throws Exception {
        JsonNode response =
                contract.path("paths")
                        .path(path)
                        .path(method)
                        .path("responses")
                        .path(Integer.toString(result.getResponse().getStatus()));
        assertThat(response.isMissingNode()).as("documented response for %s", path).isFalse();
        assertSchema(
                mapper.readTree(result.getResponse().getContentAsString()),
                response.at("/content/application~1json/schema"));
    }

    // Covers the subset used by this contract: refs, composition, required fields, types, nulls and
    // enums.
    // It intentionally does not claim to be a general OpenAPI/JSON Schema validator.
    private void assertSchema(JsonNode value, JsonNode schema) {
        assertThat(schema.isMissingNode()).isFalse();
        if (schema.has("$ref")) {
            assertSchema(value, contract.at(schema.get("$ref").asText().substring(1)));
            return;
        }
        if (schema.has("allOf")) schema.get("allOf").forEach(part -> assertSchema(value, part));
        if (schema.has("enum")) assertThat(schema.get("enum")).anyMatch(value::equals);
        if (value.isNull()) {
            assertThat(schema.path("nullable").asBoolean()).isTrue();
            return;
        }
        switch (schema.path("type").asText()) {
            case "object" -> assertThat(value.isObject()).isTrue();
            case "string" -> assertThat(value.isTextual()).isTrue();
            case "integer" -> assertThat(value.isIntegralNumber()).isTrue();
            case "boolean" -> assertThat(value.isBoolean()).isTrue();
            default -> {}
        }
        schema.path("required")
                .forEach(
                        field ->
                                assertThat(value.has(field.asText()))
                                        .as("required %s", field)
                                        .isTrue());
        schema.path("properties")
                .fields()
                .forEachRemaining(
                        field -> {
                            if (value.has(field.getKey()))
                                assertSchema(value.get(field.getKey()), field.getValue());
                        });
    }
}
