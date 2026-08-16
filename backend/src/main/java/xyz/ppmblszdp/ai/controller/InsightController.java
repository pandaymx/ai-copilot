package xyz.ppmblszdp.ai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.dto.InsightSummaryDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.ConversationInsightService;

/**
 * 历史对话洞察与分析聚合 REST 控制器（InsightController）。
 */
@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final ConversationInsightService insightService;
    private final AuthProperties authProperties;

    public InsightController(ConversationInsightService insightService, AuthProperties authProperties) {
        this.insightService = insightService;
        this.authProperties = authProperties;
    }

    @GetMapping("/summary")
    public ResponseEntity<InsightSummaryDto> getSummary(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return ResponseEntity.ok(insightService.getLatest(userId));
    }

    @PostMapping("/refresh")
    public ResponseEntity<InsightSummaryDto> refreshSummary(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return ResponseEntity.ok(insightService.compute(userId));
    }
}
