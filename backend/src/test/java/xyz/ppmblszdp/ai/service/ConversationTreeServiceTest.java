package xyz.ppmblszdp.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.dto.BranchDto.BranchDiff;
import xyz.ppmblszdp.ai.dto.BranchDto.BranchSummary;
import xyz.ppmblszdp.ai.dto.BranchDto.MergeResult;
import xyz.ppmblszdp.ai.repository.ConversationBranchRepository;
import xyz.ppmblszdp.ai.repository.ConversationBranchRepository.BranchEntity;
import xyz.ppmblszdp.ai.repository.ConversationBranchRepository.BranchMessageEntity;

class ConversationTreeServiceTest {

    private ConversationBranchRepository repository;
    private ConversationTreeService service;

    @BeforeEach
    void setUp() {
        repository = mock(ConversationBranchRepository.class);
        service = new ConversationTreeService(repository);
    }

    @Test
    @DisplayName("首次访问自动创建默认主线分支")
    void ensureMainBranchWhenEmpty() {
        when(repository.findBranchesBySession("s-1", "u-1")).thenReturn(List.of());
        BranchEntity main = new BranchEntity("br-main", "s-1", "u-1", "主线", null, null, 100L, 100L);
        when(repository.createBranch("s-1", "u-1", "主线", null, null)).thenReturn(main);

        BranchEntity result = service.ensureMainBranch("s-1", "u-1");
        assertThat(result.id()).isEqualTo("br-main");
        assertThat(result.branchLabel()).isEqualTo("主线");
    }

    @Test
    @DisplayName("从指定消息分叉创建新分支并复制祖先链")
    void createBranchFromMessage() {
        BranchMessageEntity msg1 = new BranchMessageEntity("m1", "s-1", "u-1", "br-main", null, "user", "Hello", 100L);
        BranchMessageEntity msg2 =
                new BranchMessageEntity("m2", "s-1", "u-1", "br-main", "m1", "assistant", "Hi", 101L);

        when(repository.findMessageById("m1", "u-1")).thenReturn(Optional.of(msg1));
        BranchEntity branchA = new BranchEntity("br-a", "s-1", "u-1", "方案 A", "br-main", "m1", 200L, 200L);
        when(repository.createBranch("s-1", "u-1", "方案 A", "br-main", "m1")).thenReturn(branchA);
        when(repository.findMessagesByBranch("s-1", "br-main", "u-1")).thenReturn(List.of(msg1, msg2));
        when(repository.insertMessage(eq("s-1"), eq("u-1"), eq("br-a"), any(), eq("user"), eq("Hello")))
                .thenReturn(new BranchMessageEntity("m1-clone", "s-1", "u-1", "br-a", null, "user", "Hello", 201L));

        BranchSummary summary = service.createBranch("s-1", "u-1", "m1", "方案 A");
        assertThat(summary.branchId()).isEqualTo("br-a");
        assertThat(summary.branchLabel()).isEqualTo("方案 A");
        assertThat(summary.forkFromMessageId()).isEqualTo("m1");
    }

    @Test
    @DisplayName("正确对比两个分支的消息差异")
    void diffBranches() {
        BranchMessageEntity m1 = new BranchMessageEntity("m1", "s-1", "u-1", "br-1", null, "user", "公共问题", 100L);
        BranchMessageEntity mA = new BranchMessageEntity("mA", "s-1", "u-1", "br-1", "m1", "assistant", "回答 A", 101L);
        BranchMessageEntity mB = new BranchMessageEntity("mB", "s-1", "u-1", "br-2", "m1", "assistant", "回答 B", 102L);

        when(repository.findMessagesByBranch("s-1", "br-1", "u-1")).thenReturn(List.of(m1, mA));
        when(repository.findMessagesByBranch("s-1", "br-2", "u-1")).thenReturn(List.of(m1, mB));

        BranchDiff diff = service.diff("s-1", "u-1", "br-1", "br-2");
        assertThat(diff.commonAncestorMessageId()).isEqualTo("m1");
        assertThat(diff.branchAMessages().get(0).diffStatus()).isEqualTo("UNCHANGED");
        assertThat(diff.branchAMessages().get(1).diffStatus()).isEqualTo("MODIFIED");
        assertThat(diff.branchBMessages().get(1).diffStatus()).isEqualTo("ADDED");
    }

    @Test
    @DisplayName("分支合并将增量消息追加至目标分支")
    void mergeBranches() {
        BranchMessageEntity m1 = new BranchMessageEntity("m1", "s-1", "u-1", "br-main", null, "user", "公共", 100L);
        BranchMessageEntity m2 =
                new BranchMessageEntity("m2", "s-1", "u-1", "br-feature", "m1", "assistant", "特有方案", 101L);

        when(repository.findMessagesByBranch("s-1", "br-feature", "u-1")).thenReturn(List.of(m1, m2));
        when(repository.findMessagesByBranch("s-1", "br-main", "u-1")).thenReturn(List.of(m1));
        when(repository.insertMessage(eq("s-1"), eq("u-1"), eq("br-main"), any(), eq("assistant"), eq("特有方案")))
                .thenReturn(
                        new BranchMessageEntity("m2-merged", "s-1", "u-1", "br-main", "m1", "assistant", "特有方案", 102L));

        MergeResult res = service.merge("s-1", "u-1", "br-feature", "br-main");
        assertThat(res.success()).isTrue();
        assertThat(res.mergedMessageCount()).isEqualTo(1);
    }
}
