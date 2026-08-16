package xyz.ppmblszdp.ai.dto;

import java.util.List;

/**
 * 对话分支与版本树数据传输对象（Branch DTOs）。
 */
public class BranchDto {

    public record BranchSummary(
            String branchId,
            String sessionId,
            String branchLabel,
            String parentBranchId,
            String forkFromMessageId,
            int messageCount,
            long createdAt,
            long updatedAt) {}

    public record BranchCreateRequest(String forkFromMessageId, String label) {}

    public record BranchMergeRequest(String sourceBranchId, String targetBranchId) {}

    public record DiffMessageItem(
            String id, String branchId, String role, String content, long createdAt, String diffStatus) {}

    public record BranchDiff(
            String branchA,
            String branchB,
            String commonAncestorMessageId,
            List<DiffMessageItem> branchAMessages,
            List<DiffMessageItem> branchBMessages) {}

    public record MergeResult(boolean success, String targetBranchId, int mergedMessageCount, String message) {}

    public record TreeNode(
            String id,
            String branchId,
            String branchLabel,
            String parentId,
            String role,
            String content,
            long createdAt,
            List<TreeNode> children) {}

    public record ConversationTree(
            String sessionId, String activeBranchId, List<BranchSummary> branches, List<TreeNode> rootNodes) {}
}
