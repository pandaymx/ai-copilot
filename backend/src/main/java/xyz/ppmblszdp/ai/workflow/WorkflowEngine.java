package xyz.ppmblszdp.ai.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.service.CodeExecutionService;
import xyz.ppmblszdp.ai.tool.CalculatorTool;
import xyz.ppmblszdp.ai.tool.HttpRequestTool;

/**
 * DAG 工作流执行引擎：
 * 支持拓扑调度、条件分支跳过传播（Skip Propagation）、变量引用解析、结构化虚拟线程并发及 SSE 流式事件推送。
 */
@Component
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ExecutorService VIRTUAL_THREAD_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final ProviderRegistry providerRegistry;
    private final CodeExecutionService codeExecutionService;
    private final CalculatorTool calculatorTool;
    private final HttpRequestTool httpRequestTool;

    public WorkflowEngine(
            ProviderRegistry providerRegistry,
            CodeExecutionService codeExecutionService,
            CalculatorTool calculatorTool,
            HttpRequestTool httpRequestTool) {
        this.providerRegistry = providerRegistry;
        this.codeExecutionService = codeExecutionService;
        this.calculatorTool = calculatorTool;
        this.httpRequestTool = httpRequestTool;
    }

    /**
     * 同步或流式执行工作流定义。
     *
     * @param workflow      工作流模板定义
     * @param executionId   唯一执行 ID
     * @param inputs        用户传入的初始变量
     * @param eventConsumer SSE 事件发射回调（可为 null）
     * @return 执行记录与完整节点快照
     */
    public WorkflowExecutionRecord executeWorkflow(
            WorkflowDefinition workflow,
            String executionId,
            Map<String, Object> inputs,
            Consumer<WorkflowEvent> eventConsumer) {

        long startTime = System.currentTimeMillis();
        Consumer<WorkflowEvent> safeEmit = eventConsumer != null ? eventConsumer : (e) -> {};

        log.info(
                "[WorkflowEngine] 开始执行工作流: id={}, name={}, executionId={}",
                workflow.id(),
                workflow.name(),
                executionId);
        safeEmit.accept(WorkflowEvent.workflowStarted(executionId, workflow.id()));

        // 1. DAG 拓扑排序与环路检测
        List<WorkflowNode> orderedNodes;
        try {
            orderedNodes = topologicalSort(workflow);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[WorkflowEngine] 工作流拓扑构建失败: {}", e.getMessage());
            safeEmit.accept(WorkflowEvent.workflowFailed(executionId, workflow.id(), e.getMessage(), duration));
            return new WorkflowExecutionRecord(
                    executionId,
                    workflow.id(),
                    workflow.name(),
                    "FAILED",
                    startTime,
                    System.currentTimeMillis(),
                    duration,
                    0,
                    inputs,
                    Map.of(),
                    e.getMessage(),
                    Map.of());
        }

        // 2. 初始化执行上下文（并发安全）
        ConcurrentHashMap<String, Object> nodeOutputs = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, WorkflowExecutionRecord.NodeExecutionSnapshot> snapshots = new ConcurrentHashMap<>();
        Set<String> skippedNodes = ConcurrentHashMap.newKeySet();
        Set<String> inactiveEdges = ConcurrentHashMap.newKeySet();
        AtomicInteger totalTokens = new AtomicInteger(0);
        Map<String, Object> safeInputs = inputs != null ? new HashMap<>(inputs) : new HashMap<>();

        Map<String, List<WorkflowEdge>> incomingEdgesMap =
                workflow.edges().stream().collect(Collectors.groupingBy(WorkflowEdge::targetNodeId));
        Map<String, List<WorkflowEdge>> outgoingEdgesMap =
                workflow.edges().stream().collect(Collectors.groupingBy(WorkflowEdge::sourceNodeId));

        Map<String, Object> finalOutputs = new HashMap<>();
        String workflowError = null;

        // 3. 按拓扑序推进执行节点
        for (WorkflowNode node : orderedNodes) {
            String nodeId = node.id();
            List<WorkflowEdge> incomingEdges = incomingEdgesMap.getOrDefault(nodeId, List.of());

            // 4. 判定跳过传播（Skip Propagation）
            boolean shouldSkip = false;
            String skipReason = null;

            if (!incomingEdges.isEmpty()) {
                // 如果所有前驱节点都被 SKIPPED，或者所有入度边都被标记为 inactive，则当前节点级联跳过
                boolean allPredecessorsSkipped =
                        incomingEdges.stream().allMatch(e -> skippedNodes.contains(e.sourceNodeId()));
                boolean allIncomingInactive = incomingEdges.stream().allMatch(e -> inactiveEdges.contains(e.id()));

                if (allPredecessorsSkipped) {
                    shouldSkip = true;
                    skipReason = "所有前驱依赖节点均被跳过";
                } else if (allIncomingInactive) {
                    shouldSkip = true;
                    skipReason = "所有前驱条件分支均未命中当前节点";
                }
            }

            if (shouldSkip) {
                skippedNodes.add(nodeId);
                snapshots.put(
                        nodeId,
                        new WorkflowExecutionRecord.NodeExecutionSnapshot(
                                nodeId,
                                node.name(),
                                node.type(),
                                WorkflowNode.NodeStatus.SKIPPED,
                                null,
                                null,
                                null,
                                skipReason,
                                0L,
                                0));
                safeEmit.accept(WorkflowEvent.nodeSkipped(executionId, workflow.id(), nodeId, skipReason));
                log.debug("[WorkflowEngine] 节点跳过: id={}, name={}, reason={}", nodeId, node.name(), skipReason);

                // 将该节点的所有出边标记为 inactive，继续向下传播跳过
                for (WorkflowEdge outEdge : outgoingEdgesMap.getOrDefault(nodeId, List.of())) {
                    inactiveEdges.add(outEdge.id());
                }
                continue;
            }

            // 5. 执行具体节点
            long nodeStart = System.currentTimeMillis();
            safeEmit.accept(WorkflowEvent.nodeStarted(
                    executionId, workflow.id(), nodeId, node.name(), node.type().name()));
            log.debug("[WorkflowEngine] 节点执行开始: id={}, name={}, type={}", nodeId, node.name(), node.type());

            try {
                NodeResult result = executeNode(node, safeInputs, nodeOutputs);
                long nodeDuration = System.currentTimeMillis() - nodeStart;

                if (result.output != null) {
                    nodeOutputs.put(nodeId, result.output);
                }
                totalTokens.addAndGet(result.tokenUsage);

                snapshots.put(
                        nodeId,
                        new WorkflowExecutionRecord.NodeExecutionSnapshot(
                                nodeId,
                                node.name(),
                                node.type(),
                                WorkflowNode.NodeStatus.COMPLETED,
                                result.inputState,
                                result.output,
                                null,
                                null,
                                nodeDuration,
                                result.tokenUsage));

                safeEmit.accept(WorkflowEvent.nodeFinished(
                        executionId, workflow.id(), nodeId, result.output, nodeDuration, result.tokenUsage));

                // 处理条件分支节点的边激活状态
                if (node.type() == WorkflowNode.NodeType.CONDITION) {
                    boolean condMatched = Boolean.TRUE.equals(result.output);
                    String activeHandle = condMatched ? "true" : "false";
                    for (WorkflowEdge outEdge : outgoingEdgesMap.getOrDefault(nodeId, List.of())) {
                        String handle = outEdge.sourceHandle() != null ? outEdge.sourceHandle() : "default";
                        if (!"default".equalsIgnoreCase(handle) && !activeHandle.equalsIgnoreCase(handle)) {
                            inactiveEdges.add(outEdge.id());
                        }
                    }
                }

                if (node.type() == WorkflowNode.NodeType.OUTPUT) {
                    if (result.output instanceof Map<?, ?> map) {
                        map.forEach((k, v) -> finalOutputs.put(String.valueOf(k), v));
                    } else {
                        finalOutputs.put("output", result.output);
                    }
                }
            } catch (Exception e) {
                long nodeDuration = System.currentTimeMillis() - nodeStart;
                workflowError = "节点 [" + node.name() + "] 执行失败: " + e.getMessage();
                log.error("[WorkflowEngine] 节点执行失败: id={}, error={}", nodeId, e.getMessage(), e);

                snapshots.put(
                        nodeId,
                        new WorkflowExecutionRecord.NodeExecutionSnapshot(
                                nodeId,
                                node.name(),
                                node.type(),
                                WorkflowNode.NodeStatus.FAILED,
                                null,
                                null,
                                e.getMessage(),
                                null,
                                nodeDuration,
                                0));

                safeEmit.accept(
                        WorkflowEvent.nodeFailed(executionId, workflow.id(), nodeId, e.getMessage(), nodeDuration));
                break;
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        String status = workflowError == null ? "COMPLETED" : "FAILED";

        if (workflowError == null) {
            safeEmit.accept(WorkflowEvent.workflowCompleted(
                    executionId, workflow.id(), finalOutputs, totalDuration, totalTokens.get()));
            log.info(
                    "[WorkflowEngine] 工作流执行成功: id={}, executionId={}, duration={}ms, tokens={}",
                    workflow.id(),
                    executionId,
                    totalDuration,
                    totalTokens.get());
        } else {
            safeEmit.accept(WorkflowEvent.workflowFailed(executionId, workflow.id(), workflowError, totalDuration));
            log.warn(
                    "[WorkflowEngine] 工作流执行失败: id={}, executionId={}, error={}",
                    workflow.id(),
                    executionId,
                    workflowError);
        }

        return new WorkflowExecutionRecord(
                executionId,
                workflow.id(),
                workflow.name(),
                status,
                startTime,
                System.currentTimeMillis(),
                totalDuration,
                totalTokens.get(),
                safeInputs,
                finalOutputs,
                workflowError,
                snapshots);
    }

    private record NodeResult(Object output, Object inputState, int tokenUsage) {}

    private NodeResult executeNode(WorkflowNode node, Map<String, Object> inputs, Map<String, Object> nodeOutputs) {
        Map<String, Object> config = node.config() != null ? node.config() : Map.of();

        return switch (node.type()) {
            case INPUT -> {
                // INPUT 节点：读取用户输入，传递给下游
                yield new NodeResult(inputs, inputs, 0);
            }
            case LLM -> executeLlmNode(node, config, inputs, nodeOutputs);
            case TOOL -> executeToolNode(node, config, inputs, nodeOutputs);
            case CONDITION -> {
                String expression = String.valueOf(config.getOrDefault("expression", ""));
                boolean matched = SafeVariableResolver.evaluateCondition(expression, inputs, nodeOutputs);
                yield new NodeResult(matched, expression, 0);
            }
            case PARALLEL -> {
                // 并行聚合节点
                yield new NodeResult(nodeOutputs, config, 0);
            }
            case OUTPUT -> {
                String template = String.valueOf(config.getOrDefault("outputTemplate", ""));
                if (!template.isBlank()) {
                    String resolved = SafeVariableResolver.resolveTemplate(template, inputs, nodeOutputs);
                    yield new NodeResult(resolved, template, 0);
                }
                yield new NodeResult(nodeOutputs, config, 0);
            }
        };
    }

    private NodeResult executeLlmNode(
            WorkflowNode node,
            Map<String, Object> config,
            Map<String, Object> inputs,
            Map<String, Object> nodeOutputs) {

        String promptTemplate = String.valueOf(config.getOrDefault("promptTemplate", "${input.query}"));
        String systemPrompt = String.valueOf(config.getOrDefault("systemPrompt", "你是一个高效严谨的 AI 助手。"));
        String provider = (String) config.get("provider");
        String model = (String) config.get("model");

        String resolvedPrompt = SafeVariableResolver.resolveTemplate(promptTemplate, inputs, nodeOutputs);
        String resolvedSystem = SafeVariableResolver.resolveTemplate(systemPrompt, inputs, nodeOutputs);

        ResolvedModel resolvedModel = providerRegistry.resolve(provider, model);
        ChatModel chatModel = resolvedModel.chatModel();

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        if (!resolvedSystem.isBlank()) {
            messages.add(new SystemMessage(resolvedSystem));
        }
        messages.add(new UserMessage(resolvedPrompt));

        Prompt prompt = new Prompt(messages);
        ChatResponse response = chatModel.call(prompt);

        String textOutput = "";
        int tokens = 0;
        if (response != null
                && response.getResult() != null
                && response.getResult().getOutput() != null) {
            textOutput = response.getResult().getOutput().getText();
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                Integer total = response.getMetadata().getUsage().getTotalTokens();
                if (total != null) tokens = total;
            }
        }

        return new NodeResult(textOutput, resolvedPrompt, tokens);
    }

    private NodeResult executeToolNode(
            WorkflowNode node,
            Map<String, Object> config,
            Map<String, Object> inputs,
            Map<String, Object> nodeOutputs) {

        String toolName = String.valueOf(config.getOrDefault("toolName", "")).toLowerCase();
        Map<String, Object> rawParams = (Map<String, Object>) config.getOrDefault("toolParams", Map.of());

        Map<String, Object> resolvedParams = new HashMap<>();
        rawParams.forEach((k, v) -> {
            if (v instanceof String str) {
                resolvedParams.put(k, SafeVariableResolver.resolveTemplate(str, inputs, nodeOutputs));
            } else {
                resolvedParams.put(k, v);
            }
        });

        Object output;
        if (toolName.contains("code_execution") || toolName.contains("python") || toolName.contains("code")) {
            String language = String.valueOf(resolvedParams.getOrDefault("language", "python"));
            String code = String.valueOf(resolvedParams.getOrDefault("code", ""));
            var res = codeExecutionService.execute(language, code);
            output = res.stdout().isBlank() ? (res.stderr().isBlank() ? res.status() : res.stderr()) : res.stdout();
        } else if (toolName.contains("calculator") || toolName.contains("math")) {
            String expr = String.valueOf(resolvedParams.getOrDefault("expression", "0"));
            output = calculatorTool.calculate(expr, null);
        } else if (toolName.contains("http")) {
            String method = String.valueOf(resolvedParams.getOrDefault("method", "GET"));
            String url = String.valueOf(resolvedParams.getOrDefault("url", ""));
            String body = String.valueOf(resolvedParams.getOrDefault("body", ""));
            output = httpRequestTool.httpRequest(method, url, body, null);
        } else {
            // 模拟通用工具输出
            output = "工具 [" + toolName + "] 执行完成，入参: " + resolvedParams;
        }

        return new NodeResult(output, resolvedParams, 0);
    }

    /**
     * Kahn 算法拓扑排序与环路检测。
     */
    public List<WorkflowNode> topologicalSort(WorkflowDefinition workflow) {
        Map<String, WorkflowNode> nodeMap =
                workflow.nodes().stream().collect(Collectors.toMap(WorkflowNode::id, n -> n));

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>();

        for (WorkflowNode node : workflow.nodes()) {
            inDegree.put(node.id(), 0);
            adjList.put(node.id(), new ArrayList<>());
        }

        for (WorkflowEdge edge : workflow.edges()) {
            if (nodeMap.containsKey(edge.sourceNodeId()) && nodeMap.containsKey(edge.targetNodeId())) {
                adjList.get(edge.sourceNodeId()).add(edge.targetNodeId());
                inDegree.put(edge.targetNodeId(), inDegree.get(edge.targetNodeId()) + 1);
            }
        }

        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<WorkflowNode> result = new ArrayList<>();
        int visitedCount = 0;

        while (!queue.isEmpty()) {
            String u = queue.remove(0);
            result.add(nodeMap.get(u));
            visitedCount++;

            for (String v : adjList.get(u)) {
                inDegree.put(v, inDegree.get(v) - 1);
                if (inDegree.get(v) == 0) {
                    queue.add(v);
                }
            }
        }

        if (visitedCount < workflow.nodes().size()) {
            throw new IllegalArgumentException("工作流定义包含循环引用（Cycle detected），请检查节点连线！");
        }

        return result;
    }
}
