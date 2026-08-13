package xyz.ppmblszdp.ai.dto;

public record ImageGenerationResultDto(
        String artifactId, String prompt, String payload, String mimeType, String provider, String model) {}
