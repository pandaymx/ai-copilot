package xyz.ppmblszdp.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.ContentTemplateDto;
import xyz.ppmblszdp.ai.repository.ContentGenerationRepository;

/**
 * 结构化 AI 内容生成模板服务（ContentTemplateService）。
 */
@Service
public class ContentTemplateService {

    private static final Logger log = LoggerFactory.getLogger(ContentTemplateService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ContentGenerationRepository repository;

    private static final List<ContentTemplateDto.ContentTemplateMetadata> BUILT_IN_TEMPLATES = List.of(
            new ContentTemplateDto.ContentTemplateMetadata(
                    "weekly-report",
                    "工作周报",
                    "结构化整理本周已完成工作、重点推进事项、风险卡点及下周规划",
                    "职场效能",
                    "Calendar",
                    List.of(
                            new ContentTemplateDto.TemplateField(
                                    "project", "所属项目/团队", "如：AI Copilot 核心平台研发", true, "text"),
                            new ContentTemplateDto.TemplateField(
                                    "completed", "本周完成重点", "列举已交付功能、完成的 PR 或攻关成果", true, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "inprogress", "进行中事项", "列举当前正在进行且未闭环的任务与进度", false, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "blockers", "遇到的风险与卡点", "描述阻塞进度的问题及需要的协作支持", false, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "nextWeek", "下周工作规划", "下周首要目标与里程碑计划", true, "textarea"))),
            new ContentTemplateDto.ContentTemplateMetadata(
                    "tech-doc",
                    "技术方案设计文档",
                    "标准化生成架构背景、技术选型、详细设计、高可用与部署方案",
                    "工程研发",
                    "Code",
                    List.of(
                            new ContentTemplateDto.TemplateField("moduleName", "系统/模块名称", "如：分布式任务调度引擎", true, "text"),
                            new ContentTemplateDto.TemplateField(
                                    "background", "业务背景与痛点", "为什么做这个设计？解决什么业务痛点？", true, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "techStack", "核心技术选型", "如：Spring Boot 4.1 + PostgreSQL + Redis", true, "text"),
                            new ContentTemplateDto.TemplateField(
                                    "coreDesign", "核心架构与流程设计", "关键流程步骤、数据流走向、核心接口定义", true, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "haAndSecurity", "高可用与安全保障", "容灾、限流降级、权限控制与数据脱敏", false, "textarea"))),
            new ContentTemplateDto.ContentTemplateMetadata(
                    "meeting-minutes",
                    "会议纪要与行动清单",
                    "自动归纳会议议程、核心决议要点、待办 Action Items 及责任人",
                    "敏捷协作",
                    "Users",
                    List.of(
                            new ContentTemplateDto.TemplateField("topic", "会议主题", "如：Q3 产品路线图与技术架构评审会", true, "text"),
                            new ContentTemplateDto.TemplateField(
                                    "attendees", "参会人员", "如：张三(PM)、李四(后端)、王五(架构师)", true, "text"),
                            new ContentTemplateDto.TemplateField(
                                    "discussion", "讨论要点与原始内容", "输入会议讨论的原生文本或录音转写稿", true, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "decisions", "核心决议与共识", "达成的关键结论与方向", false, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "actionItems", "后续待办清单 (TODOs)", "事项、负责人及预期交付时间", true, "textarea"))),
            new ContentTemplateDto.ContentTemplateMetadata(
                    "email-draft",
                    "商务与工作邮件",
                    "快速拟定得体、清晰、结构严谨的中英文专业商务邮件",
                    "沟通表达",
                    "Mail",
                    List.of(
                            new ContentTemplateDto.TemplateField(
                                    "recipient", "收件人身份与关系", "如：客户技术总监 / 跨部门协作负责人", true, "text"),
                            new ContentTemplateDto.TemplateField(
                                    "purpose", "邮件核心主旨", "如：沟通项目联调排期延期及补救方案", true, "text"),
                            new ContentTemplateDto.TemplateField(
                                    "keyPoints", "背景与具体诉求", "详细列出需要对方知悉或采取行动的具体要点", true, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "tone", "邮件语气", "如：专业客观 / 诚恳歉意 / 热情商务", false, "text"))),
            new ContentTemplateDto.ContentTemplateMetadata(
                    "paper-abstract",
                    "学术论文摘要与引言",
                    "生成符合顶级学术期刊规范的研究背景、方法论、实验评估与贡献",
                    "学术研究",
                    "BookOpen",
                    List.of(
                            new ContentTemplateDto.TemplateField("title", "论文标题", "如：基于多智能体协同的自适应代码生成框架", true, "text"),
                            new ContentTemplateDto.TemplateField(
                                    "motivation", "研究动机与现状不足", "现有技术的痛点与该研究解决的问题", true, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "methodology", "核心方法与算法模型", "提出的创新架构、理论基础与算法机制", true, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "results", "实验结果与评估指标", "在基准数据集上的提升表现（如准确率+15%）", true, "textarea"))),
            new ContentTemplateDto.ContentTemplateMetadata(
                    "patent-draft",
                    "技术专利交底书",
                    "结构化撰写发明名称、技术领域、背景技术缺陷、技术方案与有益效果",
                    "知识产权",
                    "FileText",
                    List.of(
                            new ContentTemplateDto.TemplateField(
                                    "inventionName", "发明名称", "如：一种基于大模型流式推理的实时安全拦截方法与系统", true, "text"),
                            new ContentTemplateDto.TemplateField("field", "技术领域", "所属计算机/人工智能/网络安全技术分支", true, "text"),
                            new ContentTemplateDto.TemplateField(
                                    "flaws", "现有技术存在的缺陷", "传统方案为何无法解决该场景下的延迟或精度问题", true, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "solution", "本发明的核心技术方案", "具体实现的步骤、硬件介质及交互流程", true, "textarea"),
                            new ContentTemplateDto.TemplateField(
                                    "benefits", "有益技术效果", "相较现有技术达到的量化提升与性能优势", true, "textarea"))));

    public ContentTemplateService(
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider, ContentGenerationRepository repository) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.repository = repository;
    }

    public List<ContentTemplateDto.ContentTemplateMetadata> listTemplates() {
        return BUILT_IN_TEMPLATES;
    }

    /**
     * 生成结构化文档
     */
    public ContentTemplateDto.GenerateContentResponse generateContent(
            String userId, ContentTemplateDto.GenerateContentRequest req) {
        String id = "cgen_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String templateId = req.templateId() != null ? req.templateId() : "weekly-report";
        String title = req.title() != null && !req.title().isBlank() ? req.title() : "未命名生成文档";
        Map<String, String> inputs = req.inputs() != null ? req.inputs() : Map.of();

        var templateMeta = BUILT_IN_TEMPLATES.stream()
                .filter(t -> t.id().equals(templateId))
                .findFirst()
                .orElse(BUILT_IN_TEMPLATES.get(0));

        String markdownContent = executeGeneration(templateMeta, title, inputs, req.customPrompt());
        Map<String, Object> sections = parseSections(markdownContent);

        long now = System.currentTimeMillis();

        // 持久化存储
        try {
            String inputsJson = MAPPER.writeValueAsString(inputs);
            repository.save(id, userId, templateId, title, inputsJson, markdownContent, now);
        } catch (Exception e) {
            log.warn("保存内容生成记录失败: {}", e.getMessage());
        }

        return new ContentTemplateDto.GenerateContentResponse(id, templateId, title, markdownContent, sections, now);
    }

    public List<ContentTemplateDto.ContentGenerationHistoryItem> listHistory(String userId) {
        return repository.listByUserId(userId, 30);
    }

    public void deleteHistory(String userId, String id) {
        repository.deleteById(userId, id);
    }

    private String executeGeneration(
            ContentTemplateDto.ContentTemplateMetadata template,
            String title,
            Map<String, String> inputs,
            String customPrompt) {

        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder != null) {
            try {
                ChatClient chatClient = builder.build();
                String systemPrompt =
                        "你是一位专业的结构化内容创作与技术文档专家。请根据用户提供的模板要求与字段素材，输出格式规范、逻辑严谨、排版优美的高质量 Markdown 文档。请使用标准的 Markdown 标题、列表、加粗和引用。";

                StringBuilder userPromptBuilder = new StringBuilder();
                userPromptBuilder.append("【文档类型】: ").append(template.name()).append("\n");
                userPromptBuilder.append("【文档标题】: ").append(title).append("\n\n");
                userPromptBuilder.append("【用户填写的素材与信息】:\n");

                for (var field : template.fields()) {
                    String val = inputs.getOrDefault(field.name(), "");
                    userPromptBuilder
                            .append("- ")
                            .append(field.label())
                            .append(": ")
                            .append(val)
                            .append("\n");
                }

                if (customPrompt != null && !customPrompt.isBlank()) {
                    userPromptBuilder
                            .append("\n【附加补充要求】: ")
                            .append(customPrompt)
                            .append("\n");
                }

                String generated = chatClient
                        .prompt()
                        .system(systemPrompt)
                        .user(userPromptBuilder.toString())
                        .call()
                        .content();

                if (generated != null && !generated.isBlank()) {
                    return generated;
                }
            } catch (Exception e) {
                log.warn("调用 ChatClient 生成内容异常，降级到结构化模板拼装: {}", e.getMessage());
            }
        }

        // 离线/降级默认生成器
        return buildFallbackTemplate(template, title, inputs);
    }

    private String buildFallbackTemplate(
            ContentTemplateDto.ContentTemplateMetadata template, String title, Map<String, String> inputs) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("> **生成类型**: ")
                .append(template.name())
                .append(" ｜ **生成时间**: ")
                .append(new java.util.Date())
                .append("\n\n");

        for (var field : template.fields()) {
            sb.append("## ").append(field.label()).append("\n\n");
            String val = inputs.getOrDefault(field.name(), "").trim();
            if (val.isBlank()) {
                sb.append("*(暂未填写，可在此直接补充编辑)*\n\n");
            } else {
                sb.append(val).append("\n\n");
            }
        }

        return sb.toString();
    }

    private Map<String, Object> parseSections(String markdown) {
        Map<String, Object> sections = new HashMap<>();
        String[] lines = markdown.split("\n");
        String currentSection = "summary";
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("## ")) {
                if (!currentContent.isEmpty()) {
                    sections.put(currentSection, currentContent.toString().trim());
                    currentContent.setLength(0);
                }
                currentSection = line.substring(3).trim();
            } else {
                currentContent.append(line).append("\n");
            }
        }
        if (!currentContent.isEmpty()) {
            sections.put(currentSection, currentContent.toString().trim());
        }

        return sections;
    }
}
