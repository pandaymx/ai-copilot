package xyz.ppmblszdp.ai.persona.dto;

import java.util.List;

/**
 * 智能体角色定义 DTO（Persona Store 核心模型）。
 *
 * @param id                角色唯一标识符
 * @param name              角色名称（如：全栈架构师）
 * @param description       角色职责与能力简述
 * @param avatar            头像图标或 Emoji（如：🏗️）
 * @param category          分类分类（如：开发架构、产品设计、测试质量、文档写作、安全审计、数据分析、界面设计、性能调优）
 * @param systemPrompt      专属角色系统提示词
 * @param temperature       推荐温度参数（如 0.3 ~ 0.8）
 * @param toolWhitelist     工具白名单（空表示不限制）
 * @param preferredProvider 首选模型供应商
 * @param preferredModel    首选模型名称
 * @param tags              标签列表
 * @param isBuiltin         是否为系统内置预设角色
 * @param creatorUserId     创建者用户 ID（内置为 system）
 * @param createdAtMs       创建时间戳
 * @param updatedAtMs       更新时间戳
 */
public record PersonaDto(
        String id,
        String name,
        String description,
        String avatar,
        String category,
        String systemPrompt,
        Double temperature,
        List<String> toolWhitelist,
        String preferredProvider,
        String preferredModel,
        List<String> tags,
        boolean isBuiltin,
        String creatorUserId,
        long createdAtMs,
        long updatedAtMs) {

    public static PersonaDto ofBuiltin(
            String id,
            String name,
            String description,
            String avatar,
            String category,
            String systemPrompt,
            Double temperature,
            List<String> toolWhitelist,
            String preferredProvider,
            String preferredModel,
            List<String> tags) {
        long now = System.currentTimeMillis();
        return new PersonaDto(
                id,
                name,
                description,
                avatar,
                category,
                systemPrompt,
                temperature,
                toolWhitelist != null ? toolWhitelist : List.of(),
                preferredProvider,
                preferredModel,
                tags != null ? tags : List.of(),
                true,
                "system",
                now,
                now);
    }
}
