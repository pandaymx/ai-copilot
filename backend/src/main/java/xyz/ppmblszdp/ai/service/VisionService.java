package xyz.ppmblszdp.ai.service;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import xyz.ppmblszdp.ai.dto.MediaDto;
import xyz.ppmblszdp.ai.rag.security.SsrfBlockedException;
import xyz.ppmblszdp.ai.rag.security.SsrfGuard;

/**
 * 多模态视觉服务 (VisionService)。
 *
 * <p>核心职责：
 * <ol>
 *   <li>解析转换 Base64 Data URL、原始 Base64 以及网络外部图片 URL；</li>
 *   <li>强制魔数（Magic Numbers）校验与 MIME 规范化（支持 JPG、PNG、WebP、GIF）；</li>
 *   <li>外部 URL 下载集成 SSRF 防护（拦截私网/回环/元数据地址）与体积限制（≤ 10MB）；</li>
 *   <li>构建 Spring AI {@link Media} 实体与包含多模态媒体的 {@link UserMessage}。</li>
 * </ol>
 */
@Service
public class VisionService {

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);

    /** 单张图片最大允许字节数 (10MB) */
    public static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    /** 单次对话最大允许传入图片数量 */
    public static final int MAX_MEDIA_COUNT = 4;

    /** 外部 URL 下载超时限制 (5s) */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public VisionService() {
        HttpClient httpClient =
                HttpClient.create().responseTimeout(DOWNLOAD_TIMEOUT).followRedirect(true);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IMAGE_BYTES))
                .build();

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();
    }

    public VisionService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 统一提取并转换请求中的所有图片资源为 Spring AI {@link Media} 列表。
     *
     * @param mediaDtos 包含 Base64 或 Data URL 的 MediaDto 列表（可空）
     * @param mediaUrls 外部网络图片 URL 列表（可空）
     * @return 校验通过的 {@link Media} 列表（最多 MAX_MEDIA_COUNT 张）
     */
    public List<Media> extractMedia(List<MediaDto> mediaDtos, List<String> mediaUrls) {
        List<Media> result = new ArrayList<>();

        if (mediaDtos != null) {
            for (MediaDto dto : mediaDtos) {
                if (result.size() >= MAX_MEDIA_COUNT) {
                    log.warn("图片数量超过上限 {}, 忽略后续图片", MAX_MEDIA_COUNT);
                    break;
                }
                Media m = parseMediaDto(dto);
                if (m != null) {
                    result.add(m);
                }
            }
        }

        if (mediaUrls != null) {
            for (String url : mediaUrls) {
                if (result.size() >= MAX_MEDIA_COUNT) {
                    log.warn("图片数量超过上限 {}, 忽略后续图片 URL", MAX_MEDIA_COUNT);
                    break;
                }
                if (url == null || url.isBlank()) {
                    continue;
                }
                Media m = parseUrlOrDataUri(url.trim());
                if (m != null) {
                    result.add(m);
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * 构建带有图片的多模态 UserMessage。
     */
    public UserMessage buildUserMessage(String text, List<Media> mediaList) {
        String msgText = (text != null) ? text : "";
        if (mediaList != null && !mediaList.isEmpty()) {
            return UserMessage.builder().text(msgText).media(mediaList).build();
        }
        return new UserMessage(msgText);
    }

    /**
     * 解析单个 MediaDto。
     */
    public Media parseMediaDto(MediaDto dto) {
        if (dto == null || dto.data() == null || dto.data().isBlank()) {
            return null;
        }
        return parseUrlOrDataUriWithFallbackMime(dto.data().trim(), dto.mimeType());
    }

    /**
     * 解析字符串形式的图片（支持 data:image/..., http(s)://, 或纯 base64）。
     */
    public Media parseUrlOrDataUri(String input) {
        return parseUrlOrDataUriWithFallbackMime(input, null);
    }

    private Media parseUrlOrDataUriWithFallbackMime(String input, String fallbackMime) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();

        // 1. 处理 Data URI (例如 data:image/png;base64,xxxx)
        if (trimmed.startsWith("data:") && trimmed.contains(",")) {
            return parseDataUri(trimmed);
        }

        // 2. 处理 HTTP / HTTPS 外部网络图片
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return downloadExternalImage(trimmed);
        }

        // 3. 处理纯 Base64 字符串
        return parseRawBase64(trimmed, fallbackMime);
    }

    private Media parseDataUri(String dataUri) {
        try {
            int commaIdx = dataUri.indexOf(',');
            String header = dataUri.substring(0, commaIdx);
            String base64Content = dataUri.substring(commaIdx + 1).trim();

            String declaredMime = "image/png";
            if (header.contains(":") && header.contains(";")) {
                declaredMime = header.substring(header.indexOf(':') + 1, header.indexOf(';'));
            }

            byte[] bytes = Base64.getDecoder().decode(base64Content);
            if (bytes.length > MAX_IMAGE_BYTES) {
                log.warn("图片体积超过上限 {} 字节, 实际: {} 字节", MAX_IMAGE_BYTES, bytes.length);
                return null;
            }

            String verifiedMime = detectImageMimeType(bytes, declaredMime);
            if (verifiedMime == null) {
                log.warn("图片魔数校验失败，不受支持的图像格式: declared={}", declaredMime);
                return null;
            }

            return new Media(MimeTypeUtils.parseMimeType(verifiedMime), new ByteArrayResource(bytes));
        } catch (Exception e) {
            log.warn("解析 Data URI 图片异常: {}", e.getMessage());
            return null;
        }
    }

    private Media parseRawBase64(String base64Str, String fallbackMime) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Str.trim());
            if (bytes.length > MAX_IMAGE_BYTES) {
                log.warn("图片体积超过上限 {} 字节, 实际: {} 字节", MAX_IMAGE_BYTES, bytes.length);
                return null;
            }

            String verifiedMime = detectImageMimeType(bytes, fallbackMime);
            if (verifiedMime == null) {
                log.warn("Base64 图片魔数校验失败，不受支持的图像格式: fallback={}", fallbackMime);
                return null;
            }

            return new Media(MimeTypeUtils.parseMimeType(verifiedMime), new ByteArrayResource(bytes));
        } catch (Exception e) {
            log.warn("解析 Base64 图片异常: {}", e.getMessage());
            return null;
        }
    }

    private Media downloadExternalImage(String url) {
        try {
            // SSRF 防御校验
            SsrfGuard.validate(url);

            byte[] bytes = webClient
                    .get()
                    .uri(URI.create(url))
                    .accept(
                            MediaType.IMAGE_JPEG,
                            MediaType.IMAGE_PNG,
                            MediaType.valueOf("image/webp"),
                            MediaType.valueOf("image/gif"))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(DOWNLOAD_TIMEOUT);

            if (bytes == null || bytes.length == 0) {
                log.warn("下载外部图片为空: url={}", url);
                return null;
            }

            if (bytes.length > MAX_IMAGE_BYTES) {
                log.warn("下载外部图片体积超限: url={}, bytes={}", url, bytes.length);
                return null;
            }

            String verifiedMime = detectImageMimeType(bytes, null);
            if (verifiedMime == null) {
                log.warn("下载外部图片魔数校验失败: url={}", url);
                return null;
            }

            return new Media(MimeTypeUtils.parseMimeType(verifiedMime), new ByteArrayResource(bytes));
        } catch (SsrfBlockedException e) {
            log.warn("[SSRF 防护] 拦截多模态图片下载: url={} reason={}", url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("下载外部图片失败: url={} reason={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 基于文件头 Magic Number 严格校验图片真实格式。
     *
     * @param bytes 图像二进制数据
     * @param fallbackMime 无法确切推断时的候选 MIME
     * @return 规范化后的 MIME 类型（如 image/jpeg, image/png, image/webp, image/gif），不合法则返回 null
     */
    public static String detectImageMimeType(byte[] bytes, String fallbackMime) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        // JPEG: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // PNG: 89 50 4E 47
        if ((bytes[0] & 0xFF) == 0x89
                && (bytes[1] & 0xFF) == 0x50
                && (bytes[2] & 0xFF) == 0x4E
                && (bytes[3] & 0xFF) == 0x47) {
            return "image/png";
        }

        // GIF: 47 49 46 38 ('GIF8')
        if ((bytes[0] & 0xFF) == 0x47
                && (bytes[1] & 0xFF) == 0x49
                && (bytes[2] & 0xFF) == 0x46
                && (bytes[3] & 0xFF) == 0x38) {
            return "image/gif";
        }

        // WebP: RIFF....WEBP
        if (bytes.length >= 12
                && (bytes[0] & 0xFF) == 0x52
                && (bytes[1] & 0xFF) == 0x49
                && (bytes[2] & 0xFF) == 0x46
                && (bytes[3] & 0xFF) == 0x46
                && (bytes[8] & 0xFF) == 0x57
                && (bytes[9] & 0xFF) == 0x45
                && (bytes[10] & 0xFF) == 0x42
                && (bytes[11] & 0xFF) == 0x50) {
            return "image/webp";
        }

        // 未通过任何已知魔数校验，视为不受支持或伪造格式，拒绝解析
        return null;
    }
}
