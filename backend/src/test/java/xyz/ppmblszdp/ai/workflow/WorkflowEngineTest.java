package xyz.ppmblszdp.ai.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import xyz.ppmblszdp.ai.registry.ModelDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderDescriptor;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.ResolvedModel;
import xyz.ppmblszdp.ai.service.CodeExecutionService;
import xyz.ppmblszdp.ai.tool.CalculatorTool;
import xyz.ppmblszdp.ai.tool.HttpRequestTool;

class WorkflowEngineTest {

    private ProviderRegistry providerRegistry;
    private CodeExecutionService codeExecutionService;
    private CalculatorTool calculatorTool;
    private HttpRequestTool httpRequestTool;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        providerRegistry = mock(ProviderRegistry.class);
        codeExecutionService = mock(CodeExecutionService.class);
        calculatorTool = mock(CalculatorTool.class);
        httpRequestTool = mock(HttpRequestTool.class);

        ChatModel mockChatModel = mock(ChatModel.class);
        ChatResponse mockResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("LLM 生成的分析研报内容"))));
        when(mockChatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        ProviderDescriptor provider = ProviderDescriptor.builder()
                .providerId("openai")
                .chatModel(mockChatModel)
                .build();
        ModelDescriptor model =
                ModelDescriptor.builder().id("gpt-4o").modelName("gpt-4o").build();
        ResolvedModel resolved = new ResolvedModel(mockChatModel, provider, model);

        when(providerRegistry.resolve(any(), any())).thenReturn(resolved);

        engine = new WorkflowEngine(providerRegistry, codeExecutionService, calculatorTool, httpRequestTool);
    }

    @Test
    @DisplayName("应该支持正确拓扑排序，并在存在环路时抛出异常")
    void testTopologicalSortAndCycleDetection() {
        List<WorkflowNode> nodes = List.of(
                new WorkflowNode("n1", "A", WorkflowNode.NodeType.INPUT, Map.of(), null),
                new WorkflowNode("n2", "B", WorkflowNode.NodeType.LLM, Map.of(), null),
                new WorkflowNode("n3", "C", WorkflowNode.NodeType.OUTPUT, Map.of(), null));

        List<WorkflowEdge> validEdges = List.of(
                new WorkflowEdge("e1", "n1", "n2", "out", "in", ""),
                new WorkflowEdge("e2", "n2", "n3", "out", "in", ""));

        WorkflowDefinition validWf = new WorkflowDefinition(
                "wf-valid", "Valid", "", "Sparkles", "1.0", List.of(), nodes, validEdges, Map.of(), 0L, 0L);

        List<WorkflowNode> sorted = engine.topologicalSort(validWf);
        assertThat(sorted).hasSize(3);
        assertThat(sorted.get(0).id()).isEqualTo("n1");
        assertThat(sorted.get(1).id()).isEqualTo("n2");
        assertThat(sorted.get(2).id()).isEqualTo("n3");

        // 引入循环连线: n3 -> n1
        List<WorkflowEdge> cycleEdges = List.of(
                new WorkflowEdge("e1", "n1", "n2", "out", "in", ""),
                new WorkflowEdge("e2", "n2", "n3", "out", "in", ""),
                new WorkflowEdge("e3", "n3", "n1", "out", "in", ""));

        WorkflowDefinition cycleWf = new WorkflowDefinition(
                "wf-cycle", "Cycle", "", "Sparkles", "1.0", List.of(), nodes, cycleEdges, Map.of(), 0L, 0L);

        assertThatThrownBy(() -> engine.topologicalSort(cycleWf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("循环引用");
    }

    @Test
    @DisplayName("应该验证条件分支的下游跳过传播（Skip Propagation）")
    void testConditionBranchSkipPropagation() {
        // 构建工作流：
        // node_input -> node_cond (判断 input.score > 80)
        // -> true 分支: node_pass -> node_pass_downstream
        // -> false 分支: node_fail -> node_fail_downstream
        // -> node_output (汇聚)
        List<WorkflowNode> nodes = List.of(
                new WorkflowNode("node_input", "输入", WorkflowNode.NodeType.INPUT, Map.of(), null),
                new WorkflowNode(
                        "node_cond",
                        "判定分数",
                        WorkflowNode.NodeType.CONDITION,
                        Map.of("expression", "${input.score} > 80"),
                        null),
                new WorkflowNode(
                        "node_pass", "优秀奖励", WorkflowNode.NodeType.LLM, Map.of("promptTemplate", "发放奖学金"), null),
                new WorkflowNode(
                        "node_pass_downstream",
                        "发送奖学金通知",
                        WorkflowNode.NodeType.LLM,
                        Map.of("promptTemplate", "恭喜"),
                        null),
                new WorkflowNode(
                        "node_fail", "补考辅导", WorkflowNode.NodeType.LLM, Map.of("promptTemplate", "参加补考"), null),
                new WorkflowNode(
                        "node_fail_downstream",
                        "发送补考通知",
                        WorkflowNode.NodeType.LLM,
                        Map.of("promptTemplate", "请准备补考"),
                        null),
                new WorkflowNode(
                        "node_output", "结束汇总", WorkflowNode.NodeType.OUTPUT, Map.of("outputTemplate", "处理完成"), null));

        List<WorkflowEdge> edges = List.of(
                new WorkflowEdge("e1", "node_input", "node_cond", "out", "in", ""),
                new WorkflowEdge("e2", "node_cond", "node_pass", "true", "in", ""),
                new WorkflowEdge("e3", "node_pass", "node_pass_downstream", "out", "in", ""),
                new WorkflowEdge("e4", "node_cond", "node_fail", "false", "in", ""),
                new WorkflowEdge("e5", "node_fail", "node_fail_downstream", "out", "in", ""),
                new WorkflowEdge("e6", "node_pass_downstream", "node_output", "out", "in", ""),
                new WorkflowEdge("e7", "node_fail_downstream", "node_output", "out", "in", ""));

        WorkflowDefinition wf = new WorkflowDefinition(
                "wf-cond-test", "Condition Test", "", "Sparkles", "1.0", List.of(), nodes, edges, Map.of(), 0L, 0L);

        List<WorkflowEvent> events = new ArrayList<>();
        // 传入 score = 95 (命中 true 分支)
        WorkflowExecutionRecord record = engine.executeWorkflow(wf, "exec-100", Map.of("score", 95), events::add);

        assertThat(record.status()).isEqualTo("COMPLETED");

        // 验证快照：node_pass 和 node_pass_downstream 应该 COMPLETED
        assertThat(record.nodeSnapshots().get("node_pass").status()).isEqualTo(WorkflowNode.NodeStatus.COMPLETED);
        assertThat(record.nodeSnapshots().get("node_pass_downstream").status())
                .isEqualTo(WorkflowNode.NodeStatus.COMPLETED);

        // 验证下游跳过传播：node_fail 与 node_fail_downstream 应该被标记为 SKIPPED
        assertThat(record.nodeSnapshots().get("node_fail").status()).isEqualTo(WorkflowNode.NodeStatus.SKIPPED);
        assertThat(record.nodeSnapshots().get("node_fail_downstream").status())
                .isEqualTo(WorkflowNode.NodeStatus.SKIPPED);
        assertThat(record.nodeSnapshots().get("node_fail").skipReason()).contains("未命中");
        assertThat(record.nodeSnapshots().get("node_fail_downstream").skipReason())
                .contains("被跳过");
    }

    @Test
    @DisplayName("应该成功执行预置模板并回传完整事件流")
    void testPrebuiltTemplates() {
        WorkflowRepository repo = new WorkflowRepository();
        repo.initTemplates();

        WorkflowDefinition researchWf =
                repo.findWorkflowById("tpl-deep-research").orElseThrow();
        assertThat(researchWf.nodes()).hasSize(6);

        List<WorkflowEvent> events = new ArrayList<>();
        WorkflowExecutionRecord record =
                engine.executeWorkflow(researchWf, "exec-research-1", researchWf.defaultInputs(), events::add);

        assertThat(record.status()).isEqualTo("COMPLETED");
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).type()).isEqualTo("workflow_started");
        assertThat(events.get(events.size() - 1).type()).isEqualTo("workflow_completed");
    }
}
