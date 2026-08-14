package xyz.ppmblszdp.ai.workflow;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * 工作流模板与执行记录存储库（内存 + 预置模板）。
 */
@Repository
public class WorkflowRepository {

    private final Map<String, WorkflowDefinition> workflowStorage = new ConcurrentHashMap<>();
    private final Map<String, WorkflowExecutionRecord> executionStorage = new ConcurrentHashMap<>();

    @PostConstruct
    public void initTemplates() {
        initDeepResearchTemplate();
        initDataPipelineTemplate();
        initComplianceTemplate();
    }

    public List<WorkflowDefinition> findAllWorkflows() {
        return new ArrayList<>(workflowStorage.values());
    }

    public Optional<WorkflowDefinition> findWorkflowById(String id) {
        return Optional.ofNullable(workflowStorage.get(id));
    }

    public WorkflowDefinition saveWorkflow(WorkflowDefinition workflow) {
        long now = System.currentTimeMillis();
        WorkflowDefinition toSave = new WorkflowDefinition(
                workflow.id(),
                workflow.name(),
                workflow.description(),
                workflow.icon() != null ? workflow.icon() : "Sparkles",
                workflow.version() != null ? workflow.version() : "1.0.0",
                workflow.inputSchema() != null ? workflow.inputSchema() : List.of(),
                workflow.nodes() != null ? workflow.nodes() : List.of(),
                workflow.edges() != null ? workflow.edges() : List.of(),
                workflow.defaultInputs() != null ? workflow.defaultInputs() : Map.of(),
                workflow.createdAt() != null ? workflow.createdAt() : now,
                now);
        workflowStorage.put(toSave.id(), toSave);
        return toSave;
    }

    public boolean deleteWorkflow(String id) {
        return workflowStorage.remove(id) != null;
    }

    public void saveExecution(WorkflowExecutionRecord record) {
        executionStorage.put(record.executionId(), record);
    }

    public Optional<WorkflowExecutionRecord> findExecutionById(String executionId) {
        return Optional.ofNullable(executionStorage.get(executionId));
    }

    public List<WorkflowExecutionRecord> findExecutionsByWorkflowId(String workflowId) {
        List<WorkflowExecutionRecord> list = new ArrayList<>();
        for (WorkflowExecutionRecord record : executionStorage.values()) {
            if (workflowId == null || workflowId.equals(record.workflowId())) {
                list.add(record);
            }
        }
        list.sort((a, b) -> Long.compare(b.startTime(), a.startTime()));
        return list;
    }

    // -------------------------------------------------------------
    // 预置 3 大高价值工作流模板
    // -------------------------------------------------------------
    private void initDeepResearchTemplate() {
        String id = "tpl-deep-research";
        List<WorkflowDefinition.InputField> inputs = List.of(
                new WorkflowDefinition.InputField(
                        "topic", "研究主题", "string", "2026年人工智能多模态大模型技术演进与行业落地预测", "输入研究主题...", List.of(), true),
                new WorkflowDefinition.InputField(
                        "focusArea",
                        "重点关注领域",
                        "select",
                        "技术架构与算力效率",
                        "选择侧重点",
                        List.of("技术架构与算力效率", "企业级商业化落地", "安全对齐与合规监管"),
                        false));

        List<WorkflowNode> nodes = List.of(
                new WorkflowNode(
                        "node_input",
                        "输入研究主题",
                        WorkflowNode.NodeType.INPUT,
                        Map.of(),
                        new WorkflowNode.Position(100, 200)),
                new WorkflowNode(
                        "node_search",
                        "检索行业动态",
                        WorkflowNode.NodeType.TOOL,
                        Map.of("toolName", "web_search", "toolParams", Map.of("query", "${input.topic} 最新技术突破与行业研报")),
                        new WorkflowNode.Position(320, 200)),
                new WorkflowNode(
                        "node_extract",
                        "提炼关键事实",
                        WorkflowNode.NodeType.LLM,
                        Map.of(
                                "systemPrompt",
                                "你是一位资深科技行业分析师。从提供的信息中萃取 5 大关键技术洞见和核心数据指标。",
                                "promptTemplate",
                                "研究主题：${input.topic}\n关注重点：${input.focusArea}\n信息源：${nodes.node_search.output}\n\n请列出关键技术突破、数据对比与发展趋势："),
                        new WorkflowNode.Position(560, 200)),
                new WorkflowNode(
                        "node_outline",
                        "生成研报大纲",
                        WorkflowNode.NodeType.LLM,
                        Map.of(
                                "systemPrompt",
                                "你是一位研报架构专家，负责将分散的事实组织为严谨的三级报告大纲。",
                                "promptTemplate",
                                "基于核心发现：\n${nodes.node_extract.output}\n\n请制定一份结构清晰的万字深度研报大纲："),
                        new WorkflowNode.Position(800, 200)),
                new WorkflowNode(
                        "node_writer",
                        "撰写深度研报正文",
                        WorkflowNode.NodeType.LLM,
                        Map.of(
                                "systemPrompt",
                                "你是一位科技智库首席主笔，语言严谨犀利，结构层层递进，数据详实。",
                                "promptTemplate",
                                "请根据以下大纲与核心事实，撰写完整的深度行业分析报告，包含执行摘要、技术架构拆解、行业落地痛点与未来展望：\n\n大纲：\n${nodes.node_outline.output}\n\n素材：\n${nodes.node_extract.output}"),
                        new WorkflowNode.Position(1040, 200)),
                new WorkflowNode(
                        "node_output",
                        "输出终稿",
                        WorkflowNode.NodeType.OUTPUT,
                        Map.of("outputTemplate", "# 深度研究分析报告\n\n${nodes.node_writer.output}"),
                        new WorkflowNode.Position(1280, 200)));

        List<WorkflowEdge> edges = List.of(
                new WorkflowEdge("e1", "node_input", "node_search", "out", "in", ""),
                new WorkflowEdge("e2", "node_search", "node_extract", "out", "in", ""),
                new WorkflowEdge("e3", "node_extract", "node_outline", "out", "in", ""),
                new WorkflowEdge("e4", "node_outline", "node_writer", "out", "in", ""),
                new WorkflowEdge("e5", "node_writer", "node_output", "out", "in", ""));

        Map<String, Object> defaultInputs = Map.of(
                "topic", "2026年人工智能多模态大模型技术演进与行业落地预测",
                "focusArea", "技术架构与算力效率");

        WorkflowDefinition def = new WorkflowDefinition(
                id,
                "深度研究报告生成",
                "端到端自动化深度研报撰写（动态搜索 → 事实提炼 → 架构大纲 → 正文编写）",
                "BookOpen",
                "1.0.0",
                inputs,
                nodes,
                edges,
                defaultInputs,
                System.currentTimeMillis(),
                System.currentTimeMillis());
        workflowStorage.put(id, def);
    }

    private void initDataPipelineTemplate() {
        String id = "tpl-data-pipeline";
        List<WorkflowDefinition.InputField> inputs = List.of(
                new WorkflowDefinition.InputField(
                        "datasetName", "数据集名称", "string", "2026年Q1-Q4各渠道销售转化数据", "输入数据集描述...", List.of(), true),
                new WorkflowDefinition.InputField(
                        "metricGoal", "分析目标", "string", "计算各季度增长率与渠道转化率，并输出可视化散点趋势", "输入分析目标...", List.of(), true));

        List<WorkflowNode> nodes = List.of(
                new WorkflowNode(
                        "node_input",
                        "输入数据集信息",
                        WorkflowNode.NodeType.INPUT,
                        Map.of(),
                        new WorkflowNode.Position(100, 200)),
                new WorkflowNode(
                        "node_sandbox",
                        "代码沙箱清洗与绘图",
                        WorkflowNode.NodeType.TOOL,
                        Map.of(
                                "toolName",
                                "code_execution",
                                "toolParams",
                                Map.of(
                                        "language", "python",
                                        "code",
                                                "import numpy as np\nimport matplotlib.pyplot as plt\n\n# 模拟销售额与转化率\nquarters = ['Q1', 'Q2', 'Q3', 'Q4']\nrevenue = [120, 145, 190, 240]\nconversion = [3.2, 3.8, 4.5, 5.1]\n\nprint(f'总销售额: {sum(revenue)} 万, 平均转化率: {np.mean(conversion):.2f}%')\nfor q, r, c in zip(quarters, revenue, conversion):\n    print(f'{q}: 销售额={r}万, 转化率={c}%')\n\nplt.figure(figsize=(6, 4))\nplt.plot(quarters, revenue, marker='o', color='royalblue', label='Revenue (w)')\nplt.title('2026 Quarterly Performance')\nplt.xlabel('Quarter')\nplt.ylabel('Revenue (10k RMB)')\nplt.grid(True, linestyle='--', alpha=0.6)\nplt.legend()\nplt.savefig('quarterly_performance.png')\nprint('Chart saved: quarterly_performance.png')")),
                        new WorkflowNode.Position(360, 200)),
                new WorkflowNode(
                        "node_insights",
                        "统计洞察与建议",
                        WorkflowNode.NodeType.LLM,
                        Map.of(
                                "systemPrompt",
                                "你是一位资深商业数据分析总监，善于从运行数据中提炼关键驱动因素和下一步增长策略。",
                                "promptTemplate",
                                "数据集：${input.datasetName}\n分析目标：${input.metricGoal}\n\nPython沙箱运行统计结果：\n${nodes.node_sandbox.output}\n\n请输出 3 项关键数据发现及 3 条针对性的增长落地策略："),
                        new WorkflowNode.Position(640, 200)),
                new WorkflowNode(
                        "node_output",
                        "输出分析报告",
                        WorkflowNode.NodeType.OUTPUT,
                        Map.of(
                                "outputTemplate",
                                "## 商业智能数据分析报告\n\n### 统计汇总\n${nodes.node_sandbox.output}\n\n### 决策洞察与建议\n${nodes.node_insights.output}"),
                        new WorkflowNode.Position(920, 200)));

        List<WorkflowEdge> edges = List.of(
                new WorkflowEdge("e1", "node_input", "node_sandbox", "out", "in", ""),
                new WorkflowEdge("e2", "node_sandbox", "node_insights", "out", "in", ""),
                new WorkflowEdge("e3", "node_insights", "node_output", "out", "in", ""));

        Map<String, Object> defaultInputs = Map.of(
                "datasetName", "2026年Q1-Q4各渠道销售转化数据",
                "metricGoal", "计算各季度增长率与渠道转化率，并输出可视化趋势分析");

        WorkflowDefinition def = new WorkflowDefinition(
                id,
                "数据清洗与智能分析管道",
                "自动化数据清洗、Python 沙箱统计绘图与 AI 商业洞察报告",
                "LineChart",
                "1.0.0",
                inputs,
                nodes,
                edges,
                defaultInputs,
                System.currentTimeMillis(),
                System.currentTimeMillis());
        workflowStorage.put(id, def);
    }

    private void initComplianceTemplate() {
        String id = "tpl-compliance-localization";
        List<WorkflowDefinition.InputField> inputs = List.of(
                new WorkflowDefinition.InputField(
                        "content",
                        "待审核文案",
                        "text",
                        "新一代全智能云原生平台上线！业内遥遥领先，独家算法实现100%零延迟，全网最高性价比首发特惠！",
                        "输入文案内容...",
                        List.of(),
                        true),
                new WorkflowDefinition.InputField(
                        "targetLang",
                        "目标多语言",
                        "select",
                        "English",
                        "选择目标翻译语言",
                        List.of("English", "Japanese", "German", "Spanish"),
                        true));

        List<WorkflowNode> nodes = List.of(
                new WorkflowNode(
                        "node_input",
                        "输入文案",
                        WorkflowNode.NodeType.INPUT,
                        Map.of(),
                        new WorkflowNode.Position(100, 250)),
                new WorkflowNode(
                        "node_audit",
                        "广告法与合规审查",
                        WorkflowNode.NodeType.LLM,
                        Map.of(
                                "systemPrompt",
                                "你是一位广告法与内容安全合规专家。审查输入文案是否含有绝对化用语（如'最'、'遥遥领先'、'100%'）或虚假宣传。若完全合规请输出 'AUDIT_PASS'；若存在违规请输出 'AUDIT_FAIL: 具体违规原因'。",
                                "promptTemplate",
                                "请审查以下文案：\n${input.content}"),
                        new WorkflowNode.Position(340, 250)),
                new WorkflowNode(
                        "node_cond",
                        "判定是否合规",
                        WorkflowNode.NodeType.CONDITION,
                        Map.of("expression", "${nodes.node_audit.output.contains('AUDIT_PASS')}"),
                        new WorkflowNode.Position(580, 250)),
                new WorkflowNode(
                        "node_localize",
                        "多语言出海翻译",
                        WorkflowNode.NodeType.LLM,
                        Map.of(
                                "systemPrompt",
                                "你是一位跨国品牌本地化专家，负责将文案优雅、严谨地翻译为目标语言。",
                                "promptTemplate",
                                "原文：${input.content}\n目标语言：${input.targetLang}\n\n请输出地道、富有感染力的多语言版本："),
                        new WorkflowNode.Position(840, 150)),
                new WorkflowNode(
                        "node_fix_suggest",
                        "生成合规修改建议",
                        WorkflowNode.NodeType.LLM,
                        Map.of(
                                "systemPrompt",
                                "你是一位资深文案合规修改顾问，帮助作者规避法律风险并保留宣传卖点。",
                                "promptTemplate",
                                "原文：${input.content}\n审查意见：${nodes.node_audit.output}\n\n请给出 2 版合规安全的改写建议："),
                        new WorkflowNode.Position(840, 350)),
                new WorkflowNode(
                        "node_output",
                        "输出处理结果",
                        WorkflowNode.NodeType.OUTPUT,
                        Map.of(
                                "outputTemplate",
                                "### 合规审查与本地化结果\n\n- 审核结论: ${nodes.node_audit.output}\n- 出海翻译: ${nodes.node_localize.output}\n- 改写建议: ${nodes.node_fix_suggest.output}"),
                        new WorkflowNode.Position(1100, 250)));

        List<WorkflowEdge> edges = List.of(
                new WorkflowEdge("e1", "node_input", "node_audit", "out", "in", ""),
                new WorkflowEdge("e2", "node_audit", "node_cond", "out", "in", ""),
                new WorkflowEdge("e3", "node_cond", "node_localize", "true", "in", "合规分支 (PASS)"),
                new WorkflowEdge("e4", "node_cond", "node_fix_suggest", "false", "in", "违规分支 (FAIL)"),
                new WorkflowEdge("e5", "node_localize", "node_output", "out", "in", ""),
                new WorkflowEdge("e6", "node_fix_suggest", "node_output", "out", "in", ""));

        Map<String, Object> defaultInputs = Map.of(
                "content", "新一代全智能云原生平台上线！业内遥遥领先，独家算法实现100%零延迟，全网最高性价比首发特惠！",
                "targetLang", "English");

        WorkflowDefinition def = new WorkflowDefinition(
                id,
                "智能内容合规审查与出海本地化",
                "LLM 广告法审查 + 条件分支路由（合规则多语言翻译，违规则生成改写建议）",
                "ShieldCheck",
                "1.0.0",
                inputs,
                nodes,
                edges,
                defaultInputs,
                System.currentTimeMillis(),
                System.currentTimeMillis());
        workflowStorage.put(id, def);
    }
}
