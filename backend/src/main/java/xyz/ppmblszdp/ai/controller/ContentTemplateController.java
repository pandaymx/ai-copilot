package xyz.ppmblszdp.ai.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.ContentTemplateDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.ContentTemplateService;

/**
 * 结构化内容生成 REST 控制器（ContentTemplateController）。
 */
@RestController
@RequestMapping("/api/content")
public class ContentTemplateController {

    private final ContentTemplateService service;
    private final AuthProperties authProperties;

    public ContentTemplateController(ContentTemplateService service, AuthProperties authProperties) {
        this.service = service;
        this.authProperties = authProperties;
    }

    @GetMapping("/templates")
    public ResponseEntity<List<ContentTemplateDto.ContentTemplateMetadata>> listTemplates() {
        return ResponseEntity.ok(service.listTemplates());
    }

    @PostMapping("/generate")
    public ResponseEntity<ContentTemplateDto.GenerateContentResponse> generate(
            @RequestBody ContentTemplateDto.GenerateContentRequest req, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return ResponseEntity.ok(service.generateContent(userId, req));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ContentTemplateDto.ContentGenerationHistoryItem>> listHistory(
            ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return ResponseEntity.ok(service.listHistory(userId));
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deleteHistory(@PathVariable String id, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        service.deleteHistory(userId, id);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
