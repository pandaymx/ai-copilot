package xyz.ppmblszdp.ai.agent.multi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import xyz.ppmblszdp.ai.agent.multi.dto.*;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.factory.ChatOptionsFactory;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

/**
 * 多 Agent 协作编排引擎 (MultiAgentOrchestrator)：
 * 负责任务拓扑分解、DAG 依赖调度、虚拟线程并行执行、单节点容错、冲突检测、人机裁决与综合代理报告汇聚。
 */
@Service
public class MultiAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ExecutorService VIRTUAL_THREAD_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final ProviderRegistry registry;
    private final AiProviderProperties properties;
    private final ConflictDetector conflictDetector;

    /** 内存方案缓存，支持人机裁决（HITL）异步挂起与恢复 */
    private final ConcurrentHashMap<String, MultiAgentPlanDto> activePlans = new ConcurrentHashMap<>();

    public MultiAgentOrchestrator(
            ProviderRegistry registry, AiProviderProperties properties, ConflictDetector conflictDetector) {
        this.registry = registry;
        this.properties = properties;
        this.conflictDetector = conflictDetector;
    }

    /**
     * 流式多 Agent 协作入口。
     */
    public Flux<MultiAgentEventDto> orchestrateStream(MultiAgentRequest request) {
        Sinks.Many<MultiAgentEventDto> sink = Sinks.many().unicast().onBackpressureBuffer();

        VIRTUAL_THREAD_POOL.submit(() -> {
            try {
                executeOrchestration(request, sink);
            } catch (Exception e) {
                log.error("多 Agent 编排流水线异常", e);
                sink.tryEmitError(e);
            }
        });

        return sink.asFlux();
    }

    /**
     * 非流式多 Agent 协作入口。
     */
    public MultiAgentResponse orchestrate(MultiAgentRequest request) {
        long start = System.currentTimeMillis();
        String planId = "plan-" + UUID.randomUUID().toString().substring(0, 8);

        MultiAgentPlanDto plan = decomposeGoalIntoDag(planId, request.goal(), request.roles());
        activePlans.put(planId, plan);

        // 1. 拓扑排序与环路检测
        List<SubTaskNodeDto> safeOrder = validateAndSortDag(plan.nodes());
        Map<String, String> completedOutputs = new ConcurrentHashMap<>();
        List<SubTaskNodeDto> executedNodes = new ArrayList<>();

        // 2. 依次按依赖层级执行
        for (SubTaskNodeDto node : safeOrder) {
            if ("synthesis".equalsIgnoreCase(node.role())) {
                continue;
            }
            long nodeStart = System.currentTimeMillis();
            try {
                String promptWithDeps = buildPromptWithDependencies(node, completedOutputs);
                String output = executeAgentNode(node.role(), promptWithDeps, request.provider(), request.model());
                long nodeEnd = System.currentTimeMillis();
                SubTaskNodeDto completedNode = node.withRunning(nodeStart).withCompleted(output, nodeEnd);
                executedNodes.add(completedNode);
                completedOutputs.put(node.id(), output);
            } catch (Exception e) {
                log.warn("子代理节点 [{}] 执行失败（单节点容错生效）: {}", node.id(), e.getMessage());
                long nodeEnd = System.currentTimeMillis();
                SubTaskNodeDto failedNode = node.withRunning(nodeStart).withFailed(e.getMessage(), nodeEnd);
                executedNodes.add(failedNode);
                completedOutputs.put(node.id(), "【子代理执行异常，已降级】: " + e.getMessage());
            }
        }

        // 3. 冲突检测
        List<ConflictItemDto> conflicts = conflictDetector.detectConflicts(executedNodes);
        plan = plan.withNodes(executedNodes).withConflicts(conflicts);
        activePlans.put(planId, plan);

        // 4. 综合代理汇总
        String synthesisResult =
                executeSynthesis(request.goal(), executedNodes, conflicts, null, request.provider(), request.model());
        long totalDuration = System.currentTimeMillis() - start;

        plan = plan.withSynthesis(synthesisResult);
        activePlans.put(planId, plan);

        return new MultiAgentResponse(planId, plan.status(), plan, synthesisResult, conflicts, totalDuration);
    }

    /**
     * 用户提交冲突裁决后恢复后续 Synthesis 执行。
     */
    public MultiAgentPlanDto resolveConflictAndResume(
            ConflictResolveRequest resolveReq, String providerOverride, String modelOverride) {
        MultiAgentPlanDto plan = activePlans.get(resolveReq.planId());
        if (plan == null) {
            throw new IllegalArgumentException("未找到对应的协作计划方案: " + resolveReq.planId());
        }

        // 更新冲突裁决状态
        List<ConflictItemDto> updatedConflicts = plan.conflicts().stream()
                .map(c -> c.conflictId().equals(resolveReq.conflictId())
                        ? c.withResolution("RESOLVED_BY_USER", resolveReq.decision())
                        : c)
                .toList();

        // 重新触发综合代理生成
        String synthesisResult = executeSynthesis(
                plan.goal(), plan.nodes(), updatedConflicts, resolveReq.decision(), providerOverride, modelOverride);

        MultiAgentPlanDto finishedPlan = plan.withConflicts(updatedConflicts).withSynthesis(synthesisResult);
        activePlans.put(resolveReq.planId(), finishedPlan);
        return finishedPlan;
    }

    // ---- 内部流式核心执行流程 ----

    private void executeOrchestration(MultiAgentRequest request, Sinks.Many<MultiAgentEventDto> sink) {
        long start = System.currentTimeMillis();
        String planId = "plan-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. 任务分解
        MultiAgentPlanDto plan = decomposeGoalIntoDag(planId, request.goal(), request.roles());
        activePlans.put(planId, plan);
        sink.tryEmitNext(MultiAgentEventDto.planCreated(planId, plan));

        // 2. 拓扑排序与环路死锁保护
        List<SubTaskNodeDto> safeOrder = validateAndSortDag(plan.nodes());
        Map<String, String> completedOutputs = new ConcurrentHashMap<>();
        List<SubTaskNodeDto> updatedNodes = new ArrayList<>();

        // 按依赖层级并发分批执行 (Layer-by-Layer Parallel Execution)
        Set<String> finishedNodeIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        List<SubTaskNodeDto> remainingNodes = new ArrayList<>(safeOrder.stream()
                .filter(n -> !"synthesis".equalsIgnoreCase(n.role()))
                .toList());

        while (!remainingNodes.isEmpty()) {
            // 找出当前依赖已全部满足的就绪节点列表
            List<SubTaskNodeDto> readyNodes = remainingNodes.stream()
                    .filter(n -> finishedNodeIds.containsAll(n.dependencies()))
                    .toList();

            if (readyNodes.isEmpty()) {
                // 死锁防护兜底：若无就绪节点但还有剩余节点，强制取第一个执行
                readyNodes = List.of(remainingNodes.get(0));
            }

            remainingNodes.removeAll(readyNodes);

            // 并发执行本批次节点
            List<CompletableFuture<SubTaskNodeDto>> futures = readyNodes.stream()
                    .map(node -> CompletableFuture.supplyAsync(
                            () -> {
                                long nodeStart = System.currentTimeMillis();
                                sink.tryEmitNext(
                                        MultiAgentEventDto.agentStarted(planId, node.id(), node.role(), node.title()));

                                try {
                                    String promptWithDeps = buildPromptWithDependencies(node, completedOutputs);
                                    String output = executeAgentNode(
                                            node.role(), promptWithDeps, request.provider(), request.model());
                                    long nodeEnd = System.currentTimeMillis();
                                    long duration = Math.max(0, nodeEnd - nodeStart);

                                    SubTaskNodeDto doneNode =
                                            node.withRunning(nodeStart).withCompleted(output, nodeEnd);
                                    completedOutputs.put(node.id(), output);
                                    finishedNodeIds.add(node.id());

                                    sink.tryEmitNext(MultiAgentEventDto.agentCompleted(
                                            planId, node.id(), node.role(), node.title(), output, duration));
                                    return doneNode;
                                } catch (Exception e) {
                                    log.warn("子代理节点 [{}] 执行失败（单节点容错生效）: {}", node.id(), e.getMessage());
                                    long nodeEnd = System.currentTimeMillis();
                                    long duration = Math.max(0, nodeEnd - nodeStart);

                                    SubTaskNodeDto failedNode =
                                            node.withRunning(nodeStart).withFailed(e.getMessage(), nodeEnd);
                                    completedOutputs.put(node.id(), "【执行失败，已容错降级】: " + e.getMessage());
                                    finishedNodeIds.add(node.id());

                                    sink.tryEmitNext(MultiAgentEventDto.agentFailed(
                                            planId, node.id(), node.role(), node.title(), e.getMessage(), duration));
                                    return failedNode;
                                }
                            },
                            VIRTUAL_THREAD_POOL))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<SubTaskNodeDto> f : futures) {
                updatedNodes.add(f.join());
            }
        }

        // 3. 冲突检测
        List<ConflictItemDto> conflicts = conflictDetector.detectConflicts(updatedNodes);
        for (ConflictItemDto c : conflicts) {
            sink.tryEmitNext(MultiAgentEventDto.conflictDetected(planId, c));
        }

        plan = plan.withNodes(updatedNodes).withConflicts(conflicts);
        activePlans.put(planId, plan);

        // 4. 判断是否需要挂起等待用户裁决 (HITL)
        if (Boolean.TRUE.equals(request.interactiveConflictResolution()) && !conflicts.isEmpty()) {
            MultiAgentPlanDto waitingPlan = plan.withStatus("WAITING_USER");
            activePlans.put(planId, waitingPlan);
            sink.tryEmitNext(MultiAgentEventDto.conflictWaitingUser(planId, waitingPlan, conflicts.get(0)));
            sink.tryEmitComplete();
            return;
        }

        // 5. 综合代理汇总
        sink.tryEmitNext(MultiAgentEventDto.synthesisStarted(planId));
        String synthesisResult =
                executeSynthesis(request.goal(), updatedNodes, conflicts, null, request.provider(), request.model());

        long totalDuration = System.currentTimeMillis() - start;
        MultiAgentPlanDto finalPlan = plan.withSynthesis(synthesisResult);
        activePlans.put(planId, finalPlan);

        sink.tryEmitNext(MultiAgentEventDto.synthesisChunk(planId, synthesisResult));
        sink.tryEmitNext(MultiAgentEventDto.workflowCompleted(planId, finalPlan, totalDuration));
        sink.tryEmitComplete();
    }

    // ---- 任务智能分解 (DAG Task Decomposition) ----

    public MultiAgentPlanDto decomposeGoalIntoDag(String planId, String goal, List<String> requestedRoles) {
        String cleanGoal = goal != null ? goal.trim() : "综合对比分析";
        List<SubTaskNodeDto> nodes = new ArrayList<>();

        if (cleanGoal.contains("对比")
                || cleanGoal.contains("比较")
                || cleanGoal.contains("vs")
                || cleanGoal.contains("VS")) {
            // 对比场景：提取对比实体，拆解为多个并行调研子代理 + 1 个综合对比代理
            List<String> entities = extractCompareEntities(cleanGoal);
            List<String> parallelIds = new ArrayList<>();

            for (int i = 0; i < entities.size(); i++) {
                String entity = entities.get(i);
                String id = "task_" + (i + 1);
                parallelIds.add(id);
                nodes.add(SubTaskNodeDto.of(
                        id,
                        "research",
                        "深度调研: " + entity,
                        "全面调研目标「" + entity + "」的技术架构、核心特性、性能指标、生态成熟度与优劣势。",
                        List.of()));
            }

            // 综合汇总节点，依赖所有前置并行调研
            nodes.add(SubTaskNodeDto.of(
                    "synthesis", "synthesis", "多维对比与决策总结", "汇聚各子代理的调研成果与性能结论，形成多维对比矩阵、核心分歧解析与最终技术选型建议。", parallelIds));
        } else {
            // 通用分析与研发场景：调研 → 架构/代码设计 → 质量复核 → 综合报告
            nodes.add(SubTaskNodeDto.of(
                    "task_1", "research", "需求与技术可行性调研", "针对目标「" + cleanGoal + "」进行背景分析、方案可行性探索与技术依赖评估。", List.of()));

            nodes.add(SubTaskNodeDto.of(
                    "task_2", "code", "核心架构设计与原型实现", "基于前期调研结论，设计模块架构、接口契约与核心代码实现逻辑。", List.of("task_1")));

            nodes.add(SubTaskNodeDto.of(
                    "task_3", "analysis", "方案审查与风险推演", "对前序方案进行安全性、性能瓶颈、可维护性与潜在风险评估。", List.of("task_2")));

            nodes.add(SubTaskNodeDto.of(
                    "synthesis",
                    "synthesis",
                    "最终交付成果综合汇总",
                    "汇聚所有子代理成果，输出结构化实施方案与交付总结报告。",
                    List.of("task_1", "task_2", "task_3")));
        }

        return MultiAgentPlanDto.create(planId, cleanGoal, "多 Agent 协同规划: " + cleanGoal, nodes);
    }

    private List<String> extractCompareEntities(String goal) {
        // 简易实体分词提取（支持逗号、空格、顿号或 vs 分隔）
        String sanitized = goal.replaceAll("(?i)(请|帮我|对比|比较|分析一下|深度|如何看待)", "").trim();
        String[] parts = sanitized.split("[,，、与和/]|(?i)\\s+vs\\.?\\s+|\\s+VS\\.?\\s+|\\s+");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.length() >= 2
                    && !trimmed.contains("性能")
                    && !trimmed.contains("优缺点")
                    && !list.contains(trimmed)) {
                list.add(trimmed);
            }
        }
        if (list.size() < 2) {
            return List.of("方案 A", "方案 B", "方案 C");
        }
        return list.subList(0, Math.min(list.size(), 4));
    }

    // ---- Kahn 算法拓扑排序与环路死锁检测 ----

    public List<SubTaskNodeDto> validateAndSortDag(List<SubTaskNodeDto> nodes) {
        if (nodes == null || nodes.size() <= 1) {
            return nodes != null ? nodes : List.of();
        }

        Map<String, SubTaskNodeDto> nodeMap = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adjList = new HashMap<>();

        for (SubTaskNodeDto n : nodes) {
            nodeMap.put(n.id(), n);
            inDegree.put(n.id(), 0);
            adjList.put(n.id(), new ArrayList<>());
        }

        // 构建邻接表与入度（过滤不存在的无效依赖）
        for (SubTaskNodeDto n : nodes) {
            for (String dep : n.dependencies()) {
                if (nodeMap.containsKey(dep)) {
                    adjList.get(dep).add(n.id());
                    inDegree.put(n.id(), inDegree.get(n.id()) + 1);
                }
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<SubTaskNodeDto> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String u = queue.poll();
            sorted.add(nodeMap.get(u));
            for (String v : adjList.get(u)) {
                inDegree.put(v, inDegree.get(v) - 1);
                if (inDegree.get(v) == 0) {
                    queue.offer(v);
                }
            }
        }

        // 若排序后节点数不等于原节点数，说明存在环路或断环，自动降级为线性安全序列
        if (sorted.size() != nodes.size()) {
            log.warn("检测到 DAG 存在循环依赖或断环（拓扑节点数 {} != 总节点数 {}），自动降级为线性安全拓扑", sorted.size(), nodes.size());
            return nodes;
        }

        return sorted;
    }

    // ---- 子代理执行与成果注入 ----

    private String buildPromptWithDependencies(SubTaskNodeDto node, Map<String, String> completedOutputs) {
        StringBuilder sb = new StringBuilder();
        if (node.dependencies() != null && !node.dependencies().isEmpty()) {
            sb.append("=== 【前序子代理成果输入】 ===\n");
            for (String depId : node.dependencies()) {
                String depOutput = completedOutputs.get(depId);
                if (depOutput != null && !depOutput.isBlank()) {
                    sb.append("【来自代理 ")
                            .append(depId)
                            .append(" 的结论】:\n")
                            .append(depOutput)
                            .append("\n\n");
                }
            }
            sb.append("===============================\n\n");
        }

        sb.append("【本次子任务目标】: ").append(node.title()).append("\n");
        sb.append("【具体执行要求】: ").append(node.description());
        return sb.toString();
    }

    private String executeAgentNode(String role, String promptText, String provider, String model) {
        String systemPrompt =
                switch (role.toLowerCase()) {
                    case "research" -> "你是一个专注于深度技术调研与文献分析的专业调研代理（Research Agent）。请针对用户给出的主题，客观、详实、结构化地输出分析结论。";
                    case "code" -> "你是一个专注于高可靠软件架构与代码实现的工程代理（Code Agent）。请输出清晰的架构思路与高质量代码原型。";
                    case "analysis", "review" -> "你是一个专注于方案可行性、安全与性能审查的分析代理（Analysis Agent）。请严谨指出潜在风险与优化路径。";
                    default -> "你是一个高效的专业 AI 子代理。请专注完成指定任务。";
                };

        return callChatModel(systemPrompt, promptText, 0.3, provider, model);
    }

    private String executeSynthesis(
            String goal,
            List<SubTaskNodeDto> completedNodes,
            List<ConflictItemDto> conflicts,
            String userDecision,
            String provider,
            String model) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户总目标: ").append(goal).append("\n\n");
        sb.append("=== 各子代理执行交付成果 ===\n");
        for (SubTaskNodeDto n : completedNodes) {
            sb.append("### 子代理 [")
                    .append(n.id())
                    .append(" - ")
                    .append(n.title())
                    .append("] (角色: ")
                    .append(n.role())
                    .append(")\n");
            sb.append(
                            n.output() != null
                                    ? n.output()
                                    : (n.errorMessage() != null ? "【错误】: " + n.errorMessage() : "无输出"))
                    .append("\n\n");
        }

        if (conflicts != null && !conflicts.isEmpty()) {
            sb.append("=== 检测到的观点与事实分歧 ===\n");
            for (ConflictItemDto c : conflicts) {
                sb.append("- 分歧主题: ").append(c.topic()).append("\n");
                sb.append("  ").append(c.description()).append("\n");
                if (c.userDecision() != null) {
                    sb.append("  【用户最终裁决】: ").append(c.userDecision()).append("\n");
                }
            }
            sb.append("\n");
        }

        if (userDecision != null) {
            sb.append("【特别说明 - 用户人工裁决指示】: ").append(userDecision).append("\n\n");
        }

        String systemPrompt = "你是一个最高级别的综合汇总代理（Synthesis Agent）。\n"
                + "你的职责是整合所有子代理的独立交付成果，消除或权衡各方分歧，输出一份结构严谨、逻辑清晰、对比鲜明且具有落地指导意义的最终综合报告。\n"
                + "请使用优雅的 Markdown 排版，包含：核心结论摘要、多维对比矩阵（如适用）、分歧解析与最终建议。";

        return callChatModel(systemPrompt, sb.toString(), 0.4, provider, model);
    }

    private String callChatModel(
            String systemPrompt, String userPrompt, double temp, String providerOverride, String modelOverride) {
        final String workerProvider = providerOverride != null
                ? providerOverride
                : properties.resolveAgent().resolveWorkerProvider();
        final String workerModelId = modelOverride != null
                ? modelOverride
                : properties.resolveAgent().resolveWorkerModel();

        try {
            ResolvedModel resolved = registry.resolve(workerProvider, workerModelId);
            ChatOptions opts = ChatOptionsFactory.forProvider(resolved, temp);
            List<Message> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt));
            Prompt prompt = new Prompt(messages, opts);

            ChatResponse response = resolved.chatModel().call(prompt);
            if (response != null
                    && response.getResult() != null
                    && response.getResult().getOutput() != null) {
                String text = response.getResult().getOutput().getText();
                return text != null ? text.strip() : "";
            }
            return "";
        } catch (Exception e) {
            log.warn("调用 ChatModel ({}/{}) 失败: {}", workerProvider, workerModelId, e.getMessage());
            throw new RuntimeException("模型调用失败: " + e.getMessage(), e);
        }
    }

    public MultiAgentPlanDto getPlan(String planId) {
        return activePlans.get(planId);
    }
}
