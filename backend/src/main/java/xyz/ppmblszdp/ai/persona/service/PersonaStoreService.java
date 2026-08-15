package xyz.ppmblszdp.ai.persona.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.persona.dto.CreatePersonaReq;
import xyz.ppmblszdp.ai.persona.dto.PersonaDto;
import xyz.ppmblszdp.ai.persona.dto.PersonaMatchDto;
import xyz.ppmblszdp.ai.persona.dto.UpdatePersonaReq;

/**
 * 智能体角色市场服务（Persona Store Service）。
 *
 * <p>提供内置高质量专业人设与用户自定义角色管理，支持角色分类检索、标签过滤与意图驱动的智能角色匹配。
 */
@Service
public class PersonaStoreService {

    private static final Logger log = LoggerFactory.getLogger(PersonaStoreService.class);

    private final List<PersonaDto> builtinPersonas = new ArrayList<>();
    private final Map<String, PersonaDto> customPersonas = new ConcurrentHashMap<>();

    public PersonaStoreService() {
        initBuiltinPersonas();
    }

    private void initBuiltinPersonas() {
        builtinPersonas.add(PersonaDto.ofBuiltin(
                "architect",
                "全栈架构师",
                "专精微服务、高并发架构、系统重构、技术选型与领域驱动设计 (DDD)",
                "🏗️",
                "开发架构",
                "你是一位拥有 15+ 年大型分布式系统与云原生落地经验的资深全栈架构师。\n"
                        + "在回答时，请始终站在高可用、高并发、可扩展、低耦合与领域驱动设计（DDD）的角度审视问题。\n"
                        + "请在技术方案中明确指出关键权衡（Trade-offs）、潜在技术债务与演进路径，必要时绘制清晰的 ASCII/Mermaid 架构图与交互时序图。",
                0.4,
                List.of("workspace", "custom_tool"),
                null,
                null,
                List.of("架构设计", "DDD", "分布式", "微服务", "系统重构")));

        builtinPersonas.add(PersonaDto.ofBuiltin(
                "product_manager",
                "资深产品专家",
                "专精用户故事、PRD 需求文档、痛点挖掘、商业价值与交互路径规划",
                "💡",
                "产品设计",
                "你是一位拥有敏锐商业洞察与深度用户同理心的资深产品总监（CPO）。\n"
                        + "请用严谨的产品方法论梳理需求，涵盖：用户画像、核心痛点、场景旅程（User Journey）、功能范围（In-Scope/Out-of-Scope）、验收标准（Acceptance Criteria）与 MVP 迭代规划。\n"
                        + "始终关注真实用户价值与商业闭环，避免陷入纯粹的技术细节讨论。",
                0.7,
                List.of(),
                null,
                null,
                List.of("PRD", "需求分析", "用户体验", "MVP", "产品规划")));

        builtinPersonas.add(PersonaDto.ofBuiltin(
                "qa_expert",
                "质量与测试专家",
                "专精单元测试、集成测试、边界异常挖掘、断言设计与混沌工程",
                "🧪",
                "测试质量",
                "你是一位严谨苛刻的资深测试架构师与 QA 专家。\n"
                        + "你的使命是捍卫系统质量。在审查代码或设计时，请穷举所有可能的边界条件、并发竞态、空指针/越界、网络分区与异常降级场景。\n"
                        + "请产出高覆盖率且语义清晰的测试用例（如 JUnit 5 / Bun Test / PyTest），遵循 AAA（Arrange-Act-Assert）原则并注重等价类划分。",
                0.3,
                List.of("workspace", "code_runner"),
                null,
                null,
                List.of("单元测试", "边界条件", "QA", "自动化测试", "缺陷排查")));

        builtinPersonas.add(PersonaDto.ofBuiltin(
                "tech_writer",
                "技术写作专家",
                "专精清晰精炼的 API 文档、架构解说、开发者指南与技术博客",
                "✍️",
                "文档写作",
                "你是一位屡获殊荣的技术布道师与专业文档工程师（Technical Writer）。\n"
                        + "你的目标是让复杂的技术概念通俗易懂、行文流畅、排版优雅。\n"
                        + "请遵循 Diátaxis 文档框架（教程、指南、参考、解释），使用精确的术语、规范的代码块与重点提示 Callout（Note/Tip/Warning）。",
                0.6,
                List.of(),
                null,
                null,
                List.of("API 文档", "技术博客", "Markdown", "开发者指南", "教程")));

        builtinPersonas.add(PersonaDto.ofBuiltin(
                "security_auditor",
                "安全审计专家",
                "专精 OWASP Top 10、代码安全审查、权限越权挖掘与防御加固",
                "🛡️",
                "安全审计",
                "你是一位拥有资深红蓝对抗与合规审计经验的应用安全架构师（AppSec Expert）。\n"
                        + "请用零信任（Zero Trust）视角审查代码与架构，重点排查：注入攻击、越权漏洞（IDOR）、反序列化、敏感信息泄露、CSRF/SSRF 及供应链依赖风险。\n"
                        + "请提供符合 CWE/CVE 规范的漏洞 PoC 验证与根治加固方案。",
                0.2,
                List.of("workspace"),
                null,
                null,
                List.of("OWASP", "漏洞挖掘", "AppSec", "零信任", "安全合规")));

        builtinPersonas.add(PersonaDto.ofBuiltin(
                "data_analyst",
                "数据分析师",
                "专精 SQL 深度调优、时序指标洞察、数据建模与商业智能 BI",
                "📊",
                "数据分析",
                "你是一位精通数据科学、统计学与商业智能（BI）的高级数据分析师。\n"
                        + "请用数据说话，擅长构建高效 SQL 查询、窗口函数、聚合多维分析与漏斗转化模型。\n"
                        + "在给出结论时，请提供结构化指标公式、趋势解读与可落地的业务行动建议。",
                0.4,
                List.of("code_runner", "custom_tool"),
                null,
                null,
                List.of("SQL", "数据建模", "指标体系", "商业智能", "统计分析")));

        builtinPersonas.add(PersonaDto.ofBuiltin(
                "uiux_designer",
                "前端 UI/UX 设计师",
                "专精现代 Web 美学设计系统、React/Tailwind/CSS 动画与可访问性 (a11y)",
                "🎨",
                "界面设计",
                "你是一位追求极致视觉张力与顺滑交互的前端 UI/UX 设计总监。\n"
                        + "请在界面构建中贯彻现代设计规范（极简现代风、和谐色彩搭配、Glassmorphism、深浅色模式自适应、微交互动画与 WCAG 2.1 无障碍标准）。\n"
                        + "产出符合现代化组件库规范的精致 React / Tailwind CSS / Vanilla CSS 代码。",
                0.8,
                List.of("workspace"),
                null,
                null,
                List.of("UI/UX", "Tailwind", "CSS 动画", "现代美学", "可访问性")));

        builtinPersonas.add(PersonaDto.ofBuiltin(
                "perf_guru",
                "性能调优专家",
                "专精 JVM 深度调优、慢查询排查、并发锁优化与高吞吐低延迟工程",
                "⚡",
                "性能调优",
                "你是一位专注于极端性能极限压榨与延迟优化的性能工程大师（Performance Engineer）。\n"
                        + "请从底层原理剖析瓶颈：CPU Cache、内存分配与 GC 暂停、数据库索引选择性、异步非阻塞 I/O、无锁队列与线程池编排。\n"
                        + "请给出包含基准测试数据（Benchmark）、火焰图排查思路与量化优化预期的具体建议。",
                0.3,
                List.of("workspace", "code_runner"),
                null,
                null,
                List.of("性能压榨", "JVM", "慢查询", "高并发", "无锁编程")));
    }

    /**
     * 查询角色列表（聚合系统内置与当前用户的自定义角色，支持分类与关键字过滤）。
     */
    public List<PersonaDto> listPersonas(String userId, String category, String keyword) {
        List<PersonaDto> all = new ArrayList<>(builtinPersonas);
        if (userId != null && !userId.isBlank()) {
            customPersonas.values().stream()
                    .filter(p -> userId.equals(p.creatorUserId()))
                    .forEach(all::add);
        }

        return all.stream()
                .filter(p -> {
                    if (category != null
                            && !category.isBlank()
                            && !"ALL".equalsIgnoreCase(category)
                            && !category.equals(p.category())) {
                        return false;
                    }
                    if (keyword != null && !keyword.isBlank()) {
                        String kw = keyword.toLowerCase();
                        boolean matchName = p.name().toLowerCase().contains(kw);
                        boolean matchDesc = p.description().toLowerCase().contains(kw);
                        boolean matchTags =
                                p.tags().stream().anyMatch(t -> t.toLowerCase().contains(kw));
                        return matchName || matchDesc || matchTags;
                    }
                    return true;
                })
                .toList();
    }

    /**
     * 根据 ID 获取角色详情。
     */
    public PersonaDto getPersona(String id, String userId) {
        if (id == null || id.isBlank()) return null;

        for (PersonaDto builtin : builtinPersonas) {
            if (builtin.id().equalsIgnoreCase(id)) {
                return builtin;
            }
        }

        PersonaDto custom = customPersonas.get(id);
        if (custom != null) {
            if (custom.isBuiltin() || (userId != null && userId.equals(custom.creatorUserId()))) {
                return custom;
            }
        }

        return null;
    }

    /**
     * 创建用户自定义角色。
     */
    public PersonaDto createCustomPersona(String userId, CreatePersonaReq req) {
        String id = "custom-" + UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();

        PersonaDto persona = new PersonaDto(
                id,
                req.name(),
                req.description(),
                req.avatar() != null && !req.avatar().isBlank() ? req.avatar() : "🤖",
                req.category() != null && !req.category().isBlank() ? req.category() : "自定义助手",
                req.systemPrompt(),
                req.temperature() != null ? req.temperature() : 0.7,
                req.toolWhitelist() != null ? req.toolWhitelist() : List.of(),
                req.preferredProvider(),
                req.preferredModel(),
                req.tags() != null ? req.tags() : List.of(),
                false,
                userId != null ? userId : "anonymous",
                now,
                now);

        customPersonas.put(id, persona);
        log.info("用户 [{}] 创建自定义角色成功: id={}, name={}", userId, id, req.name());
        return persona;
    }

    /**
     * 更新用户自定义角色。
     */
    public PersonaDto updateCustomPersona(String id, String userId, UpdatePersonaReq req) {
        PersonaDto existing = customPersonas.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("角色不存在: " + id);
        }
        if (!existing.creatorUserId().equals(userId)) {
            throw new IllegalStateException("无权修改非本人创建的角色");
        }

        long now = System.currentTimeMillis();
        PersonaDto updated = new PersonaDto(
                id,
                req.name() != null ? req.name() : existing.name(),
                req.description() != null ? req.description() : existing.description(),
                req.avatar() != null ? req.avatar() : existing.avatar(),
                req.category() != null ? req.category() : existing.category(),
                req.systemPrompt() != null ? req.systemPrompt() : existing.systemPrompt(),
                req.temperature() != null ? req.temperature() : existing.temperature(),
                req.toolWhitelist() != null ? req.toolWhitelist() : existing.toolWhitelist(),
                req.preferredProvider() != null ? req.preferredProvider() : existing.preferredProvider(),
                req.preferredModel() != null ? req.preferredModel() : existing.preferredModel(),
                req.tags() != null ? req.tags() : existing.tags(),
                false,
                existing.creatorUserId(),
                existing.createdAtMs(),
                now);

        customPersonas.put(id, updated);
        log.info("用户 [{}] 更新自定义角色成功: id={}", userId, id);
        return updated;
    }

    /**
     * 删除用户自定义角色。
     */
    public boolean deleteCustomPersona(String id, String userId) {
        PersonaDto existing = customPersonas.get(id);
        if (existing == null) {
            return false;
        }
        if (!existing.creatorUserId().equals(userId)) {
            throw new IllegalStateException("无权删除非本人创建的角色");
        }
        customPersonas.remove(id);
        log.info("用户 [{}] 删除自定义角色: id={}", userId, id);
        return true;
    }

    /**
     * 意图驱动的智能角色匹配推荐。
     */
    public PersonaMatchDto.MatchResp matchPersona(String goal) {
        if (goal == null || goal.isBlank()) {
            return new PersonaMatchDto.MatchResp(builtinPersonas.get(0), 0.5, "默认推荐全栈架构师角色");
        }

        String lowerGoal = goal.toLowerCase();
        PersonaDto bestPersona = builtinPersonas.get(0);
        double maxScore = 0.0;
        String matchReason = "基于通用开发目标推荐全栈架构师";

        for (PersonaDto p : builtinPersonas) {
            double score = 0.0;
            // 匹配标签
            for (String tag : p.tags()) {
                if (lowerGoal.contains(tag.toLowerCase())) {
                    score += 0.35;
                }
            }
            // 匹配分类与名称
            if (lowerGoal.contains(p.name().toLowerCase())) {
                score += 0.5;
            }
            if (lowerGoal.contains(p.category().toLowerCase())) {
                score += 0.3;
            }
            // 特定关键词权重
            if ("qa_expert".equals(p.id())
                    && (lowerGoal.contains("test")
                            || lowerGoal.contains("测试")
                            || lowerGoal.contains("bug")
                            || lowerGoal.contains("断言"))) {
                score += 0.4;
            }
            if ("security_auditor".equals(p.id())
                    && (lowerGoal.contains("安全")
                            || lowerGoal.contains("漏洞")
                            || lowerGoal.contains("xss")
                            || lowerGoal.contains("injection")
                            || lowerGoal.contains("权限"))) {
                score += 0.4;
            }
            if ("uiux_designer".equals(p.id())
                    && (lowerGoal.contains("css")
                            || lowerGoal.contains("ui")
                            || lowerGoal.contains("界面")
                            || lowerGoal.contains("样式")
                            || lowerGoal.contains("动画")
                            || lowerGoal.contains("前端"))) {
                score += 0.4;
            }
            if ("perf_guru".equals(p.id())
                    && (lowerGoal.contains("性能")
                            || lowerGoal.contains("延迟")
                            || lowerGoal.contains("jvm")
                            || lowerGoal.contains("慢查询")
                            || lowerGoal.contains("吞吐"))) {
                score += 0.4;
            }
            if ("product_manager".equals(p.id())
                    && (lowerGoal.contains("prd")
                            || lowerGoal.contains("需求")
                            || lowerGoal.contains("用户故事")
                            || lowerGoal.contains("规划"))) {
                score += 0.4;
            }
            if ("tech_writer".equals(p.id())
                    && (lowerGoal.contains("文档")
                            || lowerGoal.contains("教程")
                            || lowerGoal.contains("博客")
                            || lowerGoal.contains("说明书"))) {
                score += 0.4;
            }

            if (score > maxScore) {
                maxScore = score;
                bestPersona = p;
                matchReason = "目标内容与【" + p.name() + "】的专业领域高度吻合";
            }
        }

        double confidence = Math.min(0.98, Math.max(0.65, maxScore));
        return new PersonaMatchDto.MatchResp(bestPersona, confidence, matchReason);
    }
}
