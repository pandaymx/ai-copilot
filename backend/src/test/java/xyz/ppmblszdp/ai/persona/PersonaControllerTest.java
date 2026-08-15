package xyz.ppmblszdp.ai.persona;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.persona.controller.PersonaController;
import xyz.ppmblszdp.ai.persona.dto.CreatePersonaReq;
import xyz.ppmblszdp.ai.persona.dto.PersonaDto;
import xyz.ppmblszdp.ai.persona.dto.PersonaMatchDto;
import xyz.ppmblszdp.ai.persona.service.PersonaStoreService;

@DisplayName("PersonaController REST 接口测试")
class PersonaControllerTest {

    private PersonaStoreService personaStoreService;
    private AuthProperties authProperties;
    private PersonaController controller;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        personaStoreService = mock(PersonaStoreService.class);
        authProperties = new AuthProperties("dev", "X-User-Id", java.util.Set.of("admin"));
        controller = new PersonaController(personaStoreService, authProperties);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    @DisplayName("GET /api/personas 应返回角色列表")
    void testListPersonas() {
        PersonaDto architect = PersonaDto.ofBuiltin(
                "architect",
                "全栈架构师",
                "专精系统架构",
                "🏗️",
                "开发架构",
                "System prompt",
                0.4,
                List.of(),
                null,
                null,
                List.of("架构"));

        when(personaStoreService.listPersonas(any(), any(), any())).thenReturn(List.of(architect));

        webTestClient
                .get()
                .uri("/api/personas")
                .header("X-User-Id", "test-user")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo("architect")
                .jsonPath("$[0].name")
                .isEqualTo("全栈架构师");
    }

    @Test
    @DisplayName("POST /api/personas 应创建自定义角色")
    void testCreatePersona() {
        CreatePersonaReq req = new CreatePersonaReq(
                "Custom Bot", "Desc", "🤖", "通用", "Prompt", 0.7, List.of(), null, null, List.of("bot"));

        PersonaDto created = new PersonaDto(
                "custom-123",
                "Custom Bot",
                "Desc",
                "🤖",
                "通用",
                "Prompt",
                0.7,
                List.of(),
                null,
                null,
                List.of("bot"),
                false,
                "test-user",
                1000L,
                1000L);

        when(personaStoreService.createCustomPersona(any(), any())).thenReturn(created);

        webTestClient
                .post()
                .uri("/api/personas")
                .header("X-User-Id", "test-user")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo("custom-123")
                .jsonPath("$.name")
                .isEqualTo("Custom Bot");
    }

    @Test
    @DisplayName("POST /api/personas/match 应返回智能推荐角色")
    void testMatchPersona() {
        PersonaDto qa = PersonaDto.ofBuiltin(
                "qa_expert", "测试专家", "专精测试", "🧪", "测试质量", "Prompt", 0.3, List.of(), null, null, List.of("测试"));

        PersonaMatchDto.MatchResp resp = new PersonaMatchDto.MatchResp(qa, 0.95, "匹配测试意图");
        when(personaStoreService.matchPersona(any())).thenReturn(resp);

        webTestClient
                .post()
                .uri("/api/personas/match")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(new PersonaMatchDto.MatchReq("写单元测试"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.recommendedPersona.id")
                .isEqualTo("qa_expert")
                .jsonPath("$.confidence")
                .isEqualTo(0.95);
    }
}
