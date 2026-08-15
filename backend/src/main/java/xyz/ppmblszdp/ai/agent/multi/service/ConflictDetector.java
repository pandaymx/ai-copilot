package xyz.ppmblszdp.ai.agent.multi.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.agent.multi.dto.ConflictItemDto;
import xyz.ppmblszdp.ai.agent.multi.dto.SubTaskNodeDto;

/**
 * 冲突检测器：在多 Agent 并行产出结论后，快速分析各子代理是否存在显著事实分歧或观点冲突。
 */
@Component
public class ConflictDetector {

    private static final Logger log = LoggerFactory.getLogger(ConflictDetector.class);

    /**
     * 对已完成的子代理节点进行快速冲突检测。
     *
     * @param completedNodes 已完成且有输出的子任务节点列表
     * @return 检测到的冲突列表（可能为空）
     */
    public List<ConflictItemDto> detectConflicts(List<SubTaskNodeDto> completedNodes) {
        List<ConflictItemDto> conflicts = new ArrayList<>();
        if (completedNodes == null || completedNodes.size() < 2) {
            return conflicts;
        }

        // 仅对具有实质输出的节点进行同层比对
        List<SubTaskNodeDto> validNodes = completedNodes.stream()
                .filter(n -> "COMPLETED".equals(n.status())
                        && n.output() != null
                        && !n.output().isBlank())
                .filter(n -> !"synthesis".equalsIgnoreCase(n.role()))
                .toList();

        for (int i = 0; i < validNodes.size(); i++) {
            for (int j = i + 1; j < validNodes.size(); j++) {
                SubTaskNodeDto nodeA = validNodes.get(i);
                SubTaskNodeDto nodeB = validNodes.get(j);

                ConflictItemDto detected = checkPairwiseConflict(nodeA, nodeB);
                if (detected != null) {
                    conflicts.add(detected);
                }
            }
        }

        log.debug("冲突检测完毕，在 {} 个节点中识别出 {} 个潜在分歧点", validNodes.size(), conflicts.size());
        return conflicts;
    }

    private ConflictItemDto checkPairwiseConflict(SubTaskNodeDto nodeA, SubTaskNodeDto nodeB) {
        String textA = nodeA.output().toLowerCase();
        String textB = nodeB.output().toLowerCase();

        // 1. 结论对立检测 (如 "优于/推荐" vs "劣于/不推荐")
        boolean aRecommends = textA.contains("推荐") || textA.contains("最佳选择") || textA.contains("明显优于");
        boolean bDisagrees = textB.contains("不推荐") || textB.contains("存在严重缺陷") || textB.contains("劣于");

        if (aRecommends && bDisagrees) {
            String topic = String.format("关于「%s」与「%s」的技术选型倾向存在分歧", nodeA.title(), nodeB.title());
            String desc =
                    String.format("代理 [%s] 倾向于积极推荐该方案，而代理 [%s] 强调了严重缺陷或不推荐，存在定性评价分歧。", nodeA.title(), nodeB.title());
            return ConflictItemDto.of(
                    UUID.randomUUID().toString(),
                    topic,
                    nodeA.id() + " (" + nodeA.title() + ")",
                    nodeB.id() + " (" + nodeB.title() + ")",
                    desc);
        }

        // 2. 性能量纲极端差异检测 (如一处断言 QPS 提升数倍，另一处断言性能下降)
        boolean aHighPerf = textA.contains("性能大幅提升") || textA.contains("吞吐量提升") || textA.contains("延迟显著降低");
        boolean bLowPerf = textB.contains("性能有所下降") || textB.contains("开销显著增加") || textB.contains("延迟明显升高");

        if (aHighPerf && bLowPerf) {
            String topic = String.format("关于「%s」与「%s」的性能指标影响结论矛盾", nodeA.title(), nodeB.title());
            String desc = String.format(
                    "代理 [%s] 报告性能与吞吐正向显著提升，但代理 [%s] 报告资源开销或延迟负向增加，需核实基准测试条件。", nodeA.title(), nodeB.title());
            return ConflictItemDto.of(
                    UUID.randomUUID().toString(),
                    topic,
                    nodeA.id() + " (" + nodeA.title() + ")",
                    nodeB.id() + " (" + nodeB.title() + ")",
                    desc);
        }

        return null;
    }
}
