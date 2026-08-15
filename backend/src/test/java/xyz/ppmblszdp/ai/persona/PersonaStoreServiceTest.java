package xyz.ppmblszdp.ai.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.persona.dto.CreatePersonaReq;
import xyz.ppmblszdp.ai.persona.dto.PersonaDto;
import xyz.ppmblszdp.ai.persona.dto.PersonaMatchDto;
import xyz.ppmblszdp.ai.persona.dto.UpdatePersonaReq;
import xyz.ppmblszdp.ai.persona.service.PersonaStoreService;

@DisplayName("PersonaStoreService 单元测试")
class PersonaStoreServiceTest {

    private PersonaStoreService personaStoreService;

    @BeforeEach
    void setUp() {
        personaStoreService = new PersonaStoreService();
    }

    @Test
    @DisplayName("应当默认加载所有系统内置高质量角色（架构师/产品/QA/写作/安全/数据/UIUX/性能）")
    void testBuiltinPersonasLoaded() {
        List<PersonaDto> list = personaStoreService.listPersonas("user-1", null, null);
        assertThat(list).isNotEmpty();
        assertThat(list).anyMatch(p -> p.id().equals("architect") && p.name().contains("架构师"));
        assertThat(list)
                .anyMatch(p -> p.id().equals("product_manager") && p.name().contains("产品"));
        assertThat(list).anyMatch(p -> p.id().equals("qa_expert") && p.name().contains("测试"));
        assertThat(list).anyMatch(p -> p.id().equals("tech_writer") && p.name().contains("写作"));
        assertThat(list)
                .anyMatch(p -> p.id().equals("security_auditor") && p.name().contains("安全"));
        assertThat(list).anyMatch(p -> p.id().equals("data_analyst") && p.name().contains("数据"));
        assertThat(list)
                .anyMatch(p -> p.id().equals("uiux_designer") && p.name().contains("UI/UX"));
        assertThat(list).anyMatch(p -> p.id().equals("perf_guru") && p.name().contains("性能"));
    }

    @Test
    @DisplayName("按分类和关键字过滤角色")
    void testFilterAndSearch() {
        List<PersonaDto> archList = personaStoreService.listPersonas("user-1", "开发架构", null);
        assertThat(archList).allMatch(p -> "开发架构".equals(p.category()));

        List<PersonaDto> searchList = personaStoreService.listPersonas("user-1", null, "微服务");
        assertThat(searchList).anyMatch(p -> p.id().equals("architect"));
    }

    @Test
    @DisplayName("用户可以创建、获取、更新和删除自定义角色，且租户间隔离")
    void testCustomPersonaCrud() {
        CreatePersonaReq createReq = new CreatePersonaReq(
                "Rust 专家",
                "专精 Rust 异步并发与内存安全",
                "🦀",
                "开发架构",
                "你是一位 Rust 专家，精通 Tokio 与所有权机制。",
                0.3,
                List.of("workspace"),
                "anthropic",
                "claude-3-5-sonnet",
                List.of("Rust", "Tokio", "并发"));

        PersonaDto created = personaStoreService.createCustomPersona("user-1", createReq);
        assertThat(created.id()).startsWith("custom-");
        assertThat(created.name()).isEqualTo("Rust 专家");
        assertThat(created.creatorUserId()).isEqualTo("user-1");

        // user-1 查询可见
        PersonaDto fetched = personaStoreService.getPersona(created.id(), "user-1");
        assertThat(fetched).isNotNull();

        // user-2 查询不可见
        PersonaDto user2Fetched = personaStoreService.getPersona(created.id(), "user-2");
        assertThat(user2Fetched).isNull();

        // user-1 更新角色
        UpdatePersonaReq updateReq = new UpdatePersonaReq(
                "Rust 架构专家",
                "专精 Rust 异步系统设计",
                "🦀",
                "开发架构",
                "你是一位 Rust 顶尖架构师。",
                0.2,
                List.of("workspace"),
                "anthropic",
                "claude-3-5-sonnet",
                List.of("Rust", "Architecture"));

        PersonaDto updated = personaStoreService.updateCustomPersona(created.id(), "user-1", updateReq);
        assertThat(updated.name()).isEqualTo("Rust 架构专家");

        // user-2 越权更新应报错
        assertThatThrownBy(() -> personaStoreService.updateCustomPersona(created.id(), "user-2", updateReq))
                .isInstanceOf(IllegalStateException.class);

        // user-1 删除角色
        boolean deleted = personaStoreService.deleteCustomPersona(created.id(), "user-1");
        assertThat(deleted).isTrue();
        assertThat(personaStoreService.getPersona(created.id(), "user-1")).isNull();
    }

    @Test
    @DisplayName("意图驱动的智能角色匹配")
    void testSmartMatchPersona() {
        PersonaMatchDto.MatchResp matchQa = personaStoreService.matchPersona("帮我编写 JUnit 单元测试并检查边界异常");
        assertThat(matchQa.recommendedPersona().id()).isEqualTo("qa_expert");
        assertThat(matchQa.confidence()).isGreaterThan(0.6);

        PersonaMatchDto.MatchResp matchSec = personaStoreService.matchPersona("排查代码中的 SQL 注入和 XSS 安全漏洞");
        assertThat(matchSec.recommendedPersona().id()).isEqualTo("security_auditor");

        PersonaMatchDto.MatchResp matchUi = personaStoreService.matchPersona("设计一套极其美观现代的 React Tailwind UI 界面");
        assertThat(matchUi.recommendedPersona().id()).isEqualTo("uiux_designer");

        PersonaMatchDto.MatchResp matchPerf = personaStoreService.matchPersona("排查 JVM GC 停顿与 MySQL 慢查询性能调优");
        assertThat(matchPerf.recommendedPersona().id()).isEqualTo("perf_guru");
    }
}
