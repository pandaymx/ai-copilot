package xyz.ppmblszdp.ai.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.repository.RedTeamRepository;
import xyz.ppmblszdp.ai.safeguard.RedTeamService;

/**
 * AI 红队安全对抗演练 REST 控制器（RedTeamController）。
 */
@RestController
@RequestMapping("/api/safeguard/redteam")
public class RedTeamController {

    private final RedTeamService redTeamService;
    private final RedTeamRepository redTeamRepository;
    private final AuthProperties authProperties;

    public RedTeamController(
            RedTeamService redTeamService, RedTeamRepository redTeamRepository, AuthProperties authProperties) {
        this.redTeamService = redTeamService;
        this.redTeamRepository = redTeamRepository;
        this.authProperties = authProperties;
    }

    @PostMapping("/run")
    public ResponseEntity<RedTeamService.RedTeamReport> runEvaluation(
            @RequestBody(required = false) Map<String, Object> body, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        int rounds = 5;
        if (body != null && body.containsKey("rounds") && body.get("rounds") instanceof Number n) {
            rounds = n.intValue();
        }
        var report = redTeamService.runEvaluation(userId, rounds);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/runs")
    public ResponseEntity<List<RedTeamRepository.RedTeamRunRecord>> listRuns(
            @RequestParam(defaultValue = "10") int limit, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return ResponseEntity.ok(redTeamRepository.listRuns(userId, limit));
    }
}
