package xyz.ppmblszdp.ai.agent.multi.controller;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import xyz.ppmblszdp.ai.agent.multi.dto.*;
import xyz.ppmblszdp.ai.agent.multi.service.MultiAgentOrchestrator;

/**
 * 多 Agent 协作与编排 REST / SSE 接口控制器。
 */
@RestController
@RequestMapping("/api/agents")
public class MultiAgentController {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentController.class);

    private final MultiAgentOrchestrator orchestrator;

    public MultiAgentController(MultiAgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * 非流式多 Agent 协作调度。
     */
    @PostMapping("/orchestrate")
    public ResponseEntity<MultiAgentResponse> orchestrate(@RequestBody MultiAgentRequest request) {
        log.info("收到多 Agent 协作请求: goal={}", request.goal());
        MultiAgentResponse response = orchestrator.orchestrate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 流式多 Agent 实时协作事件流 (SSE)。
     */
    @PostMapping(value = "/orchestrate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MultiAgentEventDto> orchestrateStream(@RequestBody MultiAgentRequest request) {
        log.info("收到流式多 Agent 协作请求: goal={}", request.goal());
        return orchestrator.orchestrateStream(request);
    }

    /**
     * 用户提交冲突裁决并恢复 Synthesis 汇总。
     */
    @PostMapping("/resolve-conflict")
    public ResponseEntity<MultiAgentPlanDto> resolveConflict(@RequestBody ConflictResolveRequest request) {
        log.info(
                "收到用户冲突裁决: planId={}, conflictId={}, decision={}",
                request.planId(),
                request.conflictId(),
                request.decision());
        MultiAgentPlanDto updatedPlan = orchestrator.resolveConflictAndResume(request, null, null);
        return ResponseEntity.ok(updatedPlan);
    }

    /**
     * 获取指定协作方案的当前状态快照。
     */
    @GetMapping("/plan/{planId}")
    public ResponseEntity<?> getPlan(@PathVariable String planId) {
        MultiAgentPlanDto plan = orchestrator.getPlan(planId);
        if (plan == null) {
            return ResponseEntity.status(404).body(Map.of("error", "未找到协作方案: " + planId));
        }
        return ResponseEntity.ok(plan);
    }
}
