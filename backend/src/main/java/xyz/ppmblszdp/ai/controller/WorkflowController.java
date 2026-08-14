package xyz.ppmblszdp.ai.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import xyz.ppmblszdp.ai.workflow.WorkflowDefinition;
import xyz.ppmblszdp.ai.workflow.WorkflowEvent;
import xyz.ppmblszdp.ai.workflow.WorkflowExecutionRecord;
import xyz.ppmblszdp.ai.workflow.WorkflowService;

/**
 * 工作流编排与执行 REST / SSE 控制器。
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public ResponseEntity<List<WorkflowDefinition>> listWorkflows() {
        return ResponseEntity.ok(workflowService.listWorkflows());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowDefinition> getWorkflow(@PathVariable("id") String id) {
        return workflowService.getWorkflow(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }

    @PostMapping
    public ResponseEntity<WorkflowDefinition> saveWorkflow(@RequestBody WorkflowDefinition workflow) {
        return ResponseEntity.ok(workflowService.saveWorkflow(workflow));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteWorkflow(@PathVariable("id") String id) {
        boolean deleted = workflowService.deleteWorkflow(id);
        return ResponseEntity.ok(Map.of("deleted", deleted, "id", id));
    }

    @PostMapping(value = "/{id}/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<WorkflowEvent> executeWorkflowStream(
            @PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> inputs) {
        Map<String, Object> effectiveInputs = inputs != null ? inputs : Map.of();
        return workflowService.executeWorkflowStream(id, effectiveInputs);
    }

    @PostMapping(value = "/{id}/execute-sync", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WorkflowExecutionRecord> executeWorkflowSync(
            @PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> inputs) {
        Map<String, Object> effectiveInputs = inputs != null ? inputs : Map.of();
        return ResponseEntity.ok(workflowService.executeWorkflowSync(id, effectiveInputs));
    }

    @GetMapping("/executions")
    public ResponseEntity<List<WorkflowExecutionRecord>> listExecutions(
            @RequestParam(value = "workflowId", required = false) String workflowId) {
        return ResponseEntity.ok(workflowService.listExecutions(workflowId));
    }

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<WorkflowExecutionRecord> getExecution(@PathVariable("executionId") String executionId) {
        return workflowService
                .getExecution(executionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
