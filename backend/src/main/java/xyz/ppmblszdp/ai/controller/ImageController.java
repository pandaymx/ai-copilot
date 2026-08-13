package xyz.ppmblszdp.ai.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.dto.ImageGenerationRequestDto;
import xyz.ppmblszdp.ai.dto.ImageGenerationResultDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.ImageGenerationService;

/**
 * 图像生成 REST 控制器。
 *
 * <ul>
 *   <li>{@code POST /api/image/generate}：直接调用图像生成服务，返回 Base64 格式结果。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/image")
public class ImageController {

    private final ImageGenerationService imageGenerationService;
    private final AuthProperties authProperties;

    public ImageController(ImageGenerationService imageGenerationService, AuthProperties authProperties) {
        this.imageGenerationService = imageGenerationService;
        this.authProperties = authProperties;
    }

    @PostMapping("/generate")
    public Mono<ImageGenerationResultDto> generate(
            @RequestBody ImageGenerationRequestDto request, ServerWebExchange exchange) {
        UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return imageGenerationService.generateImage(request);
    }
}
