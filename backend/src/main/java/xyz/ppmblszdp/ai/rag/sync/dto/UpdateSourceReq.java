package xyz.ppmblszdp.ai.rag.sync.dto;

import java.util.Map;

public record UpdateSourceReq(String name, Map<String, Object> config, String cronExpression, Boolean enabled) {}
