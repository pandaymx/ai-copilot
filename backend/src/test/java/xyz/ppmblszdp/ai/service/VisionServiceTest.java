package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import xyz.ppmblszdp.ai.dto.MediaDto;

class VisionServiceTest {

    private VisionService visionService;

    // 1x1 像素有效 PNG 二进制魔数及内容
    private static final byte[] VALID_PNG_BYTES = new byte[] {
        (byte) 0x89,
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
        0x00,
        0x00,
        0x00,
        0x0D,
        0x49,
        0x48,
        0x44,
        0x52,
        0x00,
        0x00,
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x01,
        0x08,
        0x06,
        0x00,
        0x00,
        0x00,
        0x1F,
        0x15,
        (byte) 0xC4,
        (byte) 0x89
    };

    // 简易有效 JPEG 魔数 (FF D8 FF E0)
    private static final byte[] VALID_JPEG_BYTES = new byte[] {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    // GIF89a 魔数
    private static final byte[] VALID_GIF_BYTES =
            new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00};

    // WebP 魔数 (RIFF....WEBP)
    private static final byte[] VALID_WEBP_BYTES = new byte[] {
        0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00,
        0x57, 0x45, 0x42, 0x50, 0x56, 0x50, 0x38, 0x20
    };

    @BeforeEach
    void setUp() {
        visionService = new VisionService();
    }

    @Test
    @DisplayName("解析 Data URI 格式的 PNG 图片")
    void testParseDataUriPng() {
        String base64 = Base64.getEncoder().encodeToString(VALID_PNG_BYTES);
        String dataUri = "data:image/png;base64," + base64;

        Media media = visionService.parseUrlOrDataUri(dataUri);

        assertThat(media).isNotNull();
        assertThat(media.getMimeType().toString()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("解析 Data URI 格式的 JPEG 图片")
    void testParseDataUriJpeg() {
        String base64 = Base64.getEncoder().encodeToString(VALID_JPEG_BYTES);
        String dataUri = "data:image/jpeg;base64," + base64;

        Media media = visionService.parseUrlOrDataUri(dataUri);

        assertThat(media).isNotNull();
        assertThat(media.getMimeType().toString()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("解析 MediaDto 列表与 URL 混合输入并校验上限")
    void testExtractMediaLimits() {
        String pngBase64 = Base64.getEncoder().encodeToString(VALID_PNG_BYTES);
        String jpegBase64 = Base64.getEncoder().encodeToString(VALID_JPEG_BYTES);
        String gifBase64 = Base64.getEncoder().encodeToString(VALID_GIF_BYTES);
        String webpBase64 = Base64.getEncoder().encodeToString(VALID_WEBP_BYTES);

        List<MediaDto> dtos = List.of(
                new MediaDto("image/png", pngBase64),
                new MediaDto("image/jpeg", jpegBase64),
                new MediaDto("image/gif", gifBase64),
                new MediaDto("image/webp", webpBase64),
                new MediaDto("image/png", pngBase64) // 超出 4 个的应被截断
                );

        List<Media> result = visionService.extractMedia(dtos, null);

        assertThat(result).hasSize(4);
        assertThat(result.get(0).getMimeType().toString()).isEqualTo("image/png");
        assertThat(result.get(1).getMimeType().toString()).isEqualTo("image/jpeg");
        assertThat(result.get(2).getMimeType().toString()).isEqualTo("image/gif");
        assertThat(result.get(3).getMimeType().toString()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("拦截非法文件格式与伪造 MIME")
    void testDetectInvalidFormat() {
        byte[] fakeBytes = new byte[] {0x00, 0x01, 0x02, 0x03, 0x04};
        String base64 = Base64.getEncoder().encodeToString(fakeBytes);
        String dataUri = "data:image/png;base64," + base64;

        Media media = visionService.parseUrlOrDataUri(dataUri);

        assertThat(media).isNull();
    }

    @Test
    @DisplayName("拦截 SSRF 内网地址")
    void testBlockSsrfUrl() {
        Media media = visionService.parseUrlOrDataUri("http://192.168.1.1/test.png");
        assertThat(media).isNull();

        Media loopback = visionService.parseUrlOrDataUri("http://127.0.0.1:8080/avatar.jpg");
        assertThat(loopback).isNull();
    }

    @Test
    @DisplayName("构建多模态 UserMessage")
    void testBuildUserMessage() {
        String base64 = Base64.getEncoder().encodeToString(VALID_PNG_BYTES);
        Media media = visionService.parseMediaDto(new MediaDto("image/png", base64));
        assertThat(media).isNotNull();

        UserMessage userMsg = visionService.buildUserMessage("请分析这张图", List.of(media));

        assertThat(userMsg.getText()).isEqualTo("请分析这张图");
        assertThat(userMsg.getMedia()).hasSize(1);
    }
}
