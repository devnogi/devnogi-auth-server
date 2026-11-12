package until.the.eternity.das.hero;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

class HeroValidationTest {
    @Test
    void matchesSharedFrontendContractFixtures() throws Exception {
        var fixtures =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(
                                java.nio.file.Files.readString(
                                        java.nio.file.Path.of("docs/hero-link-fixtures.json")));
        for (var fixture : fixtures) {
            String url = fixture.get("url").asText();
            if (fixture.get("valid").asBoolean()) {
                assertThatCode(() -> HeroValidation.link(url)).as(url).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> HeroValidation.link(url))
                        .as(url)
                        .isInstanceOf(HeroException.class);
            }
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    void optionalLink(String value) {
        assertThat(HeroValidation.link(value)).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://www.memonogi.com/a?q=1#test",
                "HTTPS://WWW.MEMONOGI.COM:443/",
                "https://a.b.memonogi.com/",
                "https://guild.memonogi.com/%EA%B8%B8%EB%93%9C"
            })
    void validLinks(String value) {
        assertThat(HeroValidation.link(value)).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                " ",
                "https://memonogi.com",
                "http://www.memonogi.com",
                "/guilds",
                "//www.memonogi.com",
                "https://evil-memonogi.com",
                "https://memonogi.com.evil.com",
                "https://www.memonogi.com@evil.com",
                "https://@www.memonogi.com",
                "https://www.memonogi.com:444/",
                "https://www.memonogi.com./",
                "https://-a.memonogi.com/",
                "https://a_.memonogi.com/",
                "https://한글.memonogi.com/",
                "https://%77ww.memonogi.com/",
                "https://www.memonogi.com/%GG",
                "https://www.memonogi.com\\@evil.com/",
                "https://www.memonogi.com/\n"
            })
    void invalidLinks(String value) {
        assertThatThrownBy(() -> HeroValidation.link(value)).isInstanceOf(HeroException.class);
    }

    @Test
    void validatesDecodedImage() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        assertThatCode(
                        () ->
                                HeroMedia.validate(
                                        new MockMultipartFile(
                                                "image",
                                                "hero.png",
                                                "image/png",
                                                bytes.toByteArray())))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                HeroMedia.validate(
                                        new MockMultipartFile(
                                                "image",
                                                "hero.png",
                                                "image/png",
                                                "not image".getBytes())))
                .isInstanceOf(HeroException.class);
        assertThatThrownBy(
                        () ->
                                HeroMedia.validate(
                                        new MockMultipartFile(
                                                "image",
                                                "../hero.png",
                                                "image/png",
                                                bytes.toByteArray())))
                .isInstanceOf(HeroException.class);
        assertThatThrownBy(
                        () ->
                                HeroMedia.validate(
                                        new MockMultipartFile(
                                                "image",
                                                "hero.png",
                                                "image/png",
                                                new byte[10 * 1024 * 1024 + 1])))
                .isInstanceOf(HeroException.class);
    }

    @Test
    void rejectsOversizedDimensionsBeforeAllocatingPixels() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] image = output.toByteArray();
        java.nio.ByteBuffer.wrap(image).putInt(16, 6000).putInt(20, 4001);
        var checksum = new java.util.zip.CRC32();
        checksum.update(image, 12, 17);
        java.nio.ByteBuffer.wrap(image).putInt(29, (int) checksum.getValue());
        assertThatThrownBy(
                        () ->
                                HeroMedia.validate(
                                        new MockMultipartFile(
                                                "image", "large.png", "image/png", image)))
                .isInstanceOf(HeroException.class)
                .hasMessageContaining("2,400만");
    }

    @Test
    void acceptsJpegAndRejectsDisguisedGif() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", jpeg);
        assertThatCode(
                        () ->
                                HeroMedia.validate(
                                        new MockMultipartFile(
                                                "image",
                                                "hero.jpg",
                                                "image/jpeg",
                                                jpeg.toByteArray())))
                .doesNotThrowAnyException();
        var gif = new ByteArrayOutputStream();
        ImageIO.write(image, "gif", gif);
        assertThatThrownBy(
                        () ->
                                HeroMedia.validate(
                                        new MockMultipartFile(
                                                "image",
                                                "fake.png",
                                                "image/png",
                                                gif.toByteArray())))
                .isInstanceOf(HeroException.class)
                .hasMessageContaining("JPEG");
        assertThatThrownBy(() -> HeroValidation.text("  ")).isInstanceOf(HeroException.class);
    }

    @Test
    void intervalIsStartInclusiveEndExclusive() {
        Instant start = Instant.parse("2026-11-15T15:00:00Z"), end = start.plusSeconds(60);
        assertThat(HeroContentService.active(start, start, end)).isTrue();
        assertThat(HeroContentService.active(end, start, end)).isFalse();
        assertThatThrownBy(() -> HeroValidation.period(start, start))
                .isInstanceOf(HeroException.class);
    }
}
