package xyz.ppmblszdp.ai.agent.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskPlanDto;
import xyz.ppmblszdp.ai.agent.plan.dto.TaskStepDto;

class ContextPrunerTest {

    @Test
    void pruneAndFormatHistory_shouldPruneLongHistoricalObservations() {
        TaskStepDto step1 = TaskStepDto.pending(1, "克隆仓库", "克隆主干代码", "git_clone", "完成克隆")
                .withExecution("需要首先克隆仓库", "git_clone", "{\"repoUrl\":\"https://github.com/test/repo\"}")
                .withObservation("Cloning into 'repo'...\n" + "A".repeat(1000) + "\nDone.", true, null);

        TaskStepDto step2 = TaskStepDto.pending(2, "检索代码", "搜索关键文件", "code_search_regex", "匹配文件")
                .withExecution("检索控制器文件", "code_search_regex", "{\"pattern\":\"UserController\"}")
                .withObservation("Found 2 files", true, null);

        TaskStepDto step3 = TaskStepDto.pending(3, "当前正在执行", "待执行步骤", null, "");

        TaskPlanDto plan = TaskPlanDto.of("plan_123", "测试计划", "测试目标", List.of(step1, step2, step3));

        // 对第 3 步之前的历史进行剪枝格式化
        String pruned = ContextPruner.pruneAndFormatHistory(plan, 3);

        assertThat(pruned).contains("步骤 1: 克隆仓库");
        assertThat(pruned).contains("步骤 2: 检索代码");
        assertThat(pruned).doesNotContain("步骤 3");
        // 验证巨型 Observation 是否被裁剪
        assertThat(pruned).contains("已折叠裁剪剩余");
        assertThat(pruned.length()).isLessThan(1500);
    }

    @Test
    void pruneObservation_shouldLeaveShortTextIntact() {
        String shortText = "操作成功完成";
        String res = ContextPruner.pruneObservation(shortText, 300);
        assertThat(res).isEqualTo(shortText);
    }
}
