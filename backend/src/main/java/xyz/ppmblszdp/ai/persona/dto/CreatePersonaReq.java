package xyz.ppmblszdp.ai.persona.dto;

import java.util.List;

/**
 * 创建自定义智能体角色请求。
 */
public record CreatePersonaReq(
        String name,
        String description,
        String avatar,
        String category,
        String systemPrompt,
        Double temperature,
        List<String> toolWhitelist,
        String preferredProvider,
        String preferredModel,
        List<String> tags) {}
