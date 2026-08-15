package xyz.ppmblszdp.ai.persona.dto;

/**
 * 智能体角色匹配请求与响应 DTO。
 */
public final class PersonaMatchDto {

    private PersonaMatchDto() {}

    public record MatchReq(String goal) {}

    public record MatchResp(PersonaDto recommendedPersona, double confidence, String reason) {}
}
