package xyz.ppmblszdp.ai.dto;

import java.util.List;

/**
 * 结构化会话摘要与知识归档传输对象。
 *
 * @param conversationId  会话 ID
 * @param title           提炼的精炼主题/标题
 * @param summary         总体概述 (Executive Summary)
 * @param keyDecisions    关键决策与核心结论
 * @param todos           待办清单与行动项 (Action Items)
 * @param references      参考资料、技术链接或涉及的关键库
 * @param openIssues      未决问题与后续可探索点
 * @param tags            主题标签 (Topic Tags)
 * @param messageCount    参与摘要的消息数
 * @param createdAt       摘要生成时间戳 (毫秒)
 */
public record ConversationSummaryDto(
        String conversationId,
        String title,
        String summary,
        List<String> keyDecisions,
        List<String> todos,
        List<String> references,
        List<String> openIssues,
        List<String> tags,
        int messageCount,
        long createdAt) {

    public ConversationSummaryDto {
        keyDecisions = keyDecisions == null ? List.of() : List.copyOf(keyDecisions);
        todos = todos == null ? List.of() : List.copyOf(todos);
        references = references == null ? List.of() : List.copyOf(references);
        openIssues = openIssues == null ? List.of() : List.copyOf(openIssues);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
