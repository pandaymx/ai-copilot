package xyz.ppmblszdp.ai.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * 工作流应用服务：协调模板管理、流式执行与历史记录持久化。
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRepository repository;
    private final WorkflowEngine engine;

    public WorkflowService(WorkflowRepository repository, WorkflowEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    public List<WorkflowDefinition> listWorkflows() {
        return repository.findAllWorkflows();
    }

    public Optional<WorkflowDefinition> getWorkflow(String id) {
        return repository.findWorkflowById(id);
    }

    public WorkflowDefinition saveWorkflow(WorkflowDefinition workflow) {
        String id = workflow.id() != null && !workflow.id().isBlank()
                ? workflow.id()
                : "wf-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowDefinition toSave = new WorkflowDefinition(
                id,
                workflow.name() != null ? workflow.name() : "未命名工作流",
                workflow.description(),
                workflow.icon() != null ? workflow.icon() : "Sparkles",
                workflow.version() != null ? workflow.version() : "1.0.0",
                workflow.inputSchema(),
                workflow.nodes(),
                workflow.edges(),
                workflow.defaultInputs(),
                workflow.createdAt(),
                System.currentTimeMillis());
        return repository.saveWorkflow(toSave);
    }

    public boolean deleteWorkflow(String id) {
        return repository.deleteWorkflow(id);
    }

    public Flux<WorkflowEvent> executeWorkflowStream(String workflowId, Map<String, Object> inputs) {
        WorkflowDefinition workflow = repository
                .findWorkflowById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + workflowId));

        String executionId = "exec-" + UUID.randomUUID().toString().replace("-", "");
        Sinks.Many<WorkflowEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

        // 在独立线程/虚拟线程中执行引擎，避免阻塞响应流
        Thread.startVirtualThread(() -> {
            try {
                WorkflowExecutionRecord record =
                        engine.executeWorkflow(workflow, executionId, inputs, event -> sink.tryEmitNext(event));
                repository.saveExecution(record);
            } catch (Exception e) {
                log.error("[WorkflowService] 流式执行异常: {}", e.getMessage(), e);
                sink.tryEmitNext(WorkflowEvent.workflowFailed(executionId, workflowId, e.getMessage(), 0L));
            } finally {
                sink.tryEmitComplete();
            }
        });

        return sink.asFlux();
    }

    public WorkflowExecutionRecord executeWorkflowSync(String workflowId, Map<String, Object> inputs) {
        WorkflowDefinition workflow = repository
                .findWorkflowById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + workflowId));

        String executionId = "exec-" + UUID.randomUUID().toString().replace("-", "");
        WorkflowExecutionRecord record = engine.executeWorkflow(workflow, executionId, inputs, null);
        repository.saveExecution(record);
        return record;
    }

    public List<WorkflowExecutionRecord> listExecutions(String workflowId) {
        return repository.findExecutionsByWorkflowId(workflowId);
    }

    public Optional<WorkflowExecutionRecord> getExecution(String executionId) {
        return repository.findExecutionById(executionId);
    }
}
