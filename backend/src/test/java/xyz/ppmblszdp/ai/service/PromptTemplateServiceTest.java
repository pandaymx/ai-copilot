package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.dto.PromptTemplateDto;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.repository.PromptTemplateRepository;
import xyz.ppmblszdp.ai.repository.PromptTemplateRepository.PromptTemplateEntity;

class PromptTemplateServiceTest {

    private PromptTemplateRepository repository;
    private ProviderRegistry providerRegistry;
    private PromptTemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(PromptTemplateRepository.class);
        providerRegistry = mock(ProviderRegistry.class);
        service = new PromptTemplateService(repository, providerRegistry);
    }

    @Test
    @DisplayName("正确提取模板正文中的变量插槽")
    void extractVariablesFromTemplateBody() {
        String body = "请将 {{code}} 用 {{target_lang}} 重构，目标为 {{goal}}，再次引用 {{code}}。";
        List<String> vars = PromptTemplateDto.extractVariables(body);
        assertThat(vars).containsExactly("code", "target_lang", "goal");
    }

    @Test
    @DisplayName("模板变量插槽正确替换与渲染")
    void renderTemplateWithVariables() {
        PromptTemplateEntity entity = new PromptTemplateEntity(
                "tpl-1",
                "u-1",
                "代码重构",
                "描述",
                "coding",
                "Hello {{name}}, welcome to {{place}}! Unknown: {{unknown}}",
                5,
                false,
                false,
                100L,
                100L);

        when(repository.findById("tpl-1", "u-1")).thenReturn(Optional.of(entity));

        String rendered = service.render("tpl-1", "u-1", Map.of("name", "Alice", "place", "Wonderland"));
        assertThat(rendered).isEqualTo("Hello Alice, welcome to Wonderland! Unknown: {{unknown}}");
    }

    @Test
    @DisplayName("禁止修改系统预设模板")
    void disallowModifyingSystemTemplate() {
        PromptTemplateEntity sysEntity = new PromptTemplateEntity(
                "tpl-sys-1",
                PromptTemplateRepository.SYSTEM_USER_ID,
                "系统模板",
                "描述",
                "coding",
                "正文",
                5,
                false,
                true,
                100L,
                100L);

        when(repository.findById("tpl-sys-1", "u-1")).thenReturn(Optional.of(sysEntity));

        PromptTemplateDto updateDto = new PromptTemplateDto(
                "tpl-sys-1", "u-1", "新标题", "新描述", "coding", "新正文", List.of(), 5, false, false, 100L, 200L);

        assertThatThrownBy(() -> service.update(updateDto, "u-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只读");
    }

    @Test
    @DisplayName("正确创建用户自定义模板")
    void createUserTemplate() {
        PromptTemplateDto createDto = new PromptTemplateDto(
                null,
                "u-1",
                "我的提示词",
                "自用",
                "writing",
                "请帮我写一段 {{topic}} 的文案",
                List.of("topic"),
                5,
                false,
                false,
                0L,
                0L);

        String id = service.create(createDto, "u-1");
        assertThat(id).startsWith("tpl_");
        verify(repository).insert(any(PromptTemplateEntity.class));
    }
}
