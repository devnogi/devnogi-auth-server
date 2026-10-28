package until.the.eternity.das.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import until.the.eternity.das.common.constant.JwtConstant;
import until.the.eternity.das.role.entity.Role;
import until.the.eternity.das.role.entity.enums.Name;
import until.the.eternity.das.user.entity.User;

class JwtUtilTest {
    @Test
    void sampleKeyAndMillisecondsProduceValidTokensWithExpectedLifetimes() throws Exception {
        Properties sample = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".env.example"))) {
            sample.load(reader);
        }
        String key = sample.getProperty("JWT_SECRET_KEY");
        assertThat(Base64.getDecoder().decode(key)).hasSizeGreaterThanOrEqualTo(32);
        JwtConstant settings = new JwtConstant();
        settings.setSecretKey(key);
        settings.setIssuer("local-test");
        settings.setAccessTokenValidity(
                Long.parseLong(sample.getProperty("JWT_ACCESS_TOKEN_VALIDITY")));
        settings.setRefreshTokenValidity(
                Long.parseLong(sample.getProperty("JWT_REFRESH_TOKEN_VALIDITY")));
        JwtUtil jwt = new JwtUtil(settings);
        User user =
                User.builder()
                        .id(1L)
                        .email("synthetic@example.test")
                        .role(Role.builder().name(Name.USER).build())
                        .build();
        var access = jwt.extractAllClaims(jwt.generateAccessToken(user));
        var refresh = jwt.extractAllClaims(jwt.generateRefreshToken(user));
        assertThat(access.getExpiration().getTime() - access.getIssuedAt().getTime())
                .isEqualTo(3600000L);
        assertThat(refresh.getExpiration().getTime() - refresh.getIssuedAt().getTime())
                .isEqualTo(86400000L);
        assertThat(access.get("type")).isEqualTo("ACCESS");
        assertThat(refresh.get("type")).isEqualTo("REFRESH");
        assertThat(access.get("role")).isEqualTo("USER");
        assertThat(access.getId()).isNull();
        assertThat(refresh.getId()).isNotBlank();
    }

    @Test
    void backToBackRefreshTokensHaveDifferentIdsEvenWithinOneSecond() throws Exception {
        Properties sample = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".env.example"))) {
            sample.load(reader);
        }
        JwtConstant settings = new JwtConstant();
        settings.setSecretKey(sample.getProperty("JWT_SECRET_KEY"));
        settings.setIssuer("local-test");
        settings.setRefreshTokenValidity(86400000L);
        JwtUtil jwt = new JwtUtil(settings);
        User user =
                User.builder()
                        .id(1L)
                        .email("synthetic@example.test")
                        .role(Role.builder().name(Name.USER).build())
                        .build();

        String first = jwt.generateRefreshToken(user);
        String second = jwt.generateRefreshToken(user);

        assertThat(second).isNotEqualTo(first);
        assertThat(jwt.extractAllClaims(second).getId())
                .isNotEqualTo(jwt.extractAllClaims(first).getId());
    }
}
