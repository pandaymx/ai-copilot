package xyz.ppmblszdp.ai.rag.sync.dto;

import java.util.Map;

public record CreateSourceReq(
        String name, String sourceType, Map<String, Object> config, String cronExpression, Boolean enabled) {}
