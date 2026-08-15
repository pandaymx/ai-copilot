package xyz.ppmblszdp.ai.agent.multi;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import xyz.ppmblszdp.ai.agent.multi.dto.*;
import xyz.ppmblszdp.ai.agent.multi.service.ConflictDetector;
import xyz.ppmblszdp.ai.agent.multi.service.MultiAgentOrchestrator;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.config.AiProviderProperties.AgentConfig;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;

class MultiAgentOrchestratorTest {

    private ProviderRegistry registry;
    private AiProviderProperties properties;
    private ConflictDetector conflictDetector;
    private ChatModel chatModel;
    private MultiAgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = mock(ProviderRegistry.class);
        properties = mock(AiProviderProperties.class);
        conflictDetector = new ConflictDetector();
        chatModel = mock(ChatModel.class);

        AgentConfig agentConfig = mock(AgentConfig.class);
        when(agentConfig.resolveWorkerProvider()).thenReturn("openai");
        when(agentConfig.resolveWorkerModel()).thenReturn("gpt-4o");
        when(properties.resolveAgent()).thenReturn(agentConfig);

        ModelDescriptor modelDesc = mock(ModelDescriptor.class);
        when(modelDesc.id()).thenReturn("gpt-4o");
        when(modelDesc.modelName()).thenReturn("gpt-4o");
        ProviderDescriptor provDesc = mock(ProviderDescriptor.class);
        when(provDesc.providerId()).thenReturn("openai");

        ResolvedModel resolved = mock(ResolvedModel.class);
        when(resolved.chatModel()).thenReturn(chatModel);
        when(resolved.model()).thenReturn(modelDesc);
        when(resolved.provider()).thenReturn(provDesc);
        when(registry.resolve(any(), any())).thenReturn(resolved);

        // 默认模拟返回
        Generation gen = new Generation(new AssistantMessage("【子代理输出】针对该主题的深度研究与分析完成。"));
        ChatResponse resp = new ChatResponse(List.of(gen));
        when(chatModel.call(any(Prompt.class))).thenReturn(resp);

        orchestrator = new MultiAgentOrchestrator(registry, properties, conflictDetector);
    }

    @Test
    void decomposeGoalIntoDag_compareScenario_createsParallelResearchNodesAndSynthesis() {
        String goal = "对比 Spring Boot 3 与 Quarkus 与 Micronaut 的性能差异";
        MultiAgentPlanDto plan = orchestrator.decomposeGoalIntoDag("plan-test-1", goal, null);

        assertNotNull(plan);
        assertEquals("plan-test-1", plan.planId());
        assertTrue(plan.nodes().size() >= 3);

        // 最后一个节点为 synthesis，依赖前置节点
        SubTaskNodeDto synthesisNode = plan.nodes().get(plan.nodes().size() - 1);
        assertEquals("synthesis", synthesisNode.role());
        assertFalse(synthesisNode.dependencies().isEmpty());
    }

    @Test
    void validateAndSortDag_detectsCycleAndSafelyDegrades() {
        // 构建带有循环依赖的节点 A -> B -> A
        SubTaskNodeDto nodeA = SubTaskNodeDto.of("node_A", "research", "Task A", "Desc A", List.of("node_B"));
        SubTaskNodeDto nodeB = SubTaskNodeDto.of("node_B", "research", "Task B", "Desc B", List.of("node_A"));

        List<SubTaskNodeDto> input = List.of(nodeA, nodeB);
        List<SubTaskNodeDto> sorted = orchestrator.validateAndSortDag(input);

        // 验证不会抛死锁异常，而是平稳降级
        assertNotNull(sorted);
        assertEquals(2, sorted.size());
    }

    @Test
    void orchestrate_executesFullPipelineSuccessfully() {
        MultiAgentRequest request = new MultiAgentRequest(
                "对比 Spring Boot 3 与 Quarkus 的高并发表现",
                "openai",
                "gpt-4o",
                List.of("research", "analysis", "synthesis"),
                4,
                false,
                "conv-1");

        MultiAgentResponse response = orchestrator.orchestrate(request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());
        assertNotNull(response.synthesisResult());
        assertFalse(response.plan().nodes().isEmpty());
        for (SubTaskNodeDto node : response.plan().nodes()) {
            assertEquals("COMPLETED", node.status());
        }
    }

    @Test
    void orchestrate_nodeLevelFaultTolerance_doesNotCrashPipeline() {
        // 让其中一次调用报错
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("API Rate Limit Exceeded"))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("汇总完成")))));

        MultiAgentRequest request = new MultiAgentRequest("调研新技术可行性", "openai", "gpt-4o", null, 2, false, "conv-1");

        MultiAgentResponse response = orchestrator.orchestrate(request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.status());
        assertNotNull(response.synthesisResult());
    }

    @Test
    void orchestrateStream_emitsSequenceOfEvents() {
        MultiAgentRequest request =
                new MultiAgentRequest("对比 Vue 3 与 React 19 开发体验", "openai", "gpt-4o", null, 2, false, "conv-1");

        List<MultiAgentEventDto> events = new ArrayList<>();
        orchestrator.orchestrateStream(request).toIterable().forEach(events::add);

        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> "plan_created".equals(e.eventType())));
        assertTrue(events.stream().anyMatch(e -> "agent_started".equals(e.eventType())));
        assertTrue(events.stream().anyMatch(e -> "agent_completed".equals(e.eventType())));
        assertTrue(events.stream().anyMatch(e -> "workflow_completed".equals(e.eventType())));
    }

    @Test
    void resolveConflictAndResume_updatesConflictAndProducesFinalSynthesis() {
        MultiAgentRequest request =
                new MultiAgentRequest("对比方案 A 与 方案 B", "openai", "gpt-4o", null, 2, false, "conv-1");

        MultiAgentResponse initial = orchestrator.orchestrate(request);
        String planId = initial.planId();

        ConflictResolveRequest resolveReq =
                new ConflictResolveRequest(planId, "dummy-conflict-id", "选定采用方案 A 的结论", "已与团队确认");

        MultiAgentPlanDto resumedPlan = orchestrator.resolveConflictAndResume(resolveReq, "openai", "gpt-4o");
        assertNotNull(resumedPlan);
        assertEquals("COMPLETED", resumedPlan.status());
        assertNotNull(resumedPlan.synthesisResult());
    }
}
