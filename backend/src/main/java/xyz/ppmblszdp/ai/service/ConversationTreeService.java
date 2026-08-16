package xyz.ppmblszdp.ai.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.BranchDto.BranchDiff;
import xyz.ppmblszdp.ai.dto.BranchDto.BranchSummary;
import xyz.ppmblszdp.ai.dto.BranchDto.ConversationTree;
import xyz.ppmblszdp.ai.dto.BranchDto.DiffMessageItem;
import xyz.ppmblszdp.ai.dto.BranchDto.MergeResult;
import xyz.ppmblszdp.ai.dto.BranchDto.TreeNode;
import xyz.ppmblszdp.ai.repository.ConversationBranchRepository;
import xyz.ppmblszdp.ai.repository.ConversationBranchRepository.BranchEntity;
import xyz.ppmblszdp.ai.repository.ConversationBranchRepository.BranchMessageEntity;

/**
 * 对话分支与版本树核心业务服务（ConversationTreeService）。
 */
@Service
public class ConversationTreeService {

    private static final Logger log = LoggerFactory.getLogger(ConversationTreeService.class);

    private final ConversationBranchRepository repository;

    public ConversationTreeService(ConversationBranchRepository repository) {
        this.repository = repository;
    }

    public BranchEntity ensureMainBranch(String sessionId, String userId) {
        List<BranchEntity> branches = repository.findBranchesBySession(sessionId, userId);
        if (!branches.isEmpty()) {
            return branches.getFirst();
        }
        return repository.createBranch(sessionId, userId, "主线", null, null);
    }

    public List<BranchSummary> listBranches(String sessionId, String userId) {
        ensureMainBranch(sessionId, userId);
        List<BranchEntity> entities = repository.findBranchesBySession(sessionId, userId);
        return entities.stream()
                .map(e -> {
                    int count = repository.countMessagesInBranch(sessionId, e.id(), userId);
                    return new BranchSummary(
                            e.id(),
                            e.sessionId(),
                            e.branchLabel(),
                            e.parentBranchId(),
                            e.forkFromMessageId(),
                            count,
                            e.createdAt(),
                            e.updatedAt());
                })
                .toList();
    }

    public BranchSummary createBranch(String sessionId, String userId, String forkFromMessageId, String label) {
        String parentBranchId = null;
        if (forkFromMessageId != null && !forkFromMessageId.isBlank()) {
            Optional<BranchMessageEntity> parentMsgOpt = repository.findMessageById(forkFromMessageId, userId);
            if (parentMsgOpt.isEmpty()) {
                throw new IllegalArgumentException("未找到可分叉的历史消息: " + forkFromMessageId);
            }
            BranchMessageEntity parentMsg = parentMsgOpt.get();
            parentBranchId = parentMsg.branchId();
        }

        String safeLabel =
                (label != null && !label.isBlank()) ? label.trim() : "分支 " + System.currentTimeMillis() % 10000;
        BranchEntity created = repository.createBranch(sessionId, userId, safeLabel, parentBranchId, forkFromMessageId);

        // 如果从某消息分叉，复制共同祖先链上的消息到新分支以保持独立上下文
        if (forkFromMessageId != null && parentBranchId != null) {
            List<BranchMessageEntity> sourceMsgs = repository.findMessagesByBranch(sessionId, parentBranchId, userId);
            String lastParentId = null;
            for (BranchMessageEntity msg : sourceMsgs) {
                BranchMessageEntity cloned = repository.insertMessage(
                        sessionId, userId, created.id(), lastParentId, msg.role(), msg.content());
                lastParentId = cloned.id();
                if (msg.id().equals(forkFromMessageId)) {
                    break;
                }
            }
        }

        int count = repository.countMessagesInBranch(sessionId, created.id(), userId);
        log.info("创建对话分支成功: sessionId={}, branchId={}, label={}", sessionId, created.id(), created.branchLabel());
        return new BranchSummary(
                created.id(),
                created.sessionId(),
                created.branchLabel(),
                created.parentBranchId(),
                created.forkFromMessageId(),
                count,
                created.createdAt(),
                created.updatedAt());
    }

    public BranchMessageEntity appendMessage(
            String sessionId, String userId, String branchId, String parentId, String role, String content) {
        String effectiveBranchId = branchId;
        if (effectiveBranchId == null || effectiveBranchId.isBlank()) {
            effectiveBranchId = ensureMainBranch(sessionId, userId).id();
        }
        return repository.insertMessage(sessionId, userId, effectiveBranchId, parentId, role, content);
    }

    public ConversationTree assembleTree(String sessionId, String userId, String activeBranchId) {
        ensureMainBranch(sessionId, userId);
        List<BranchSummary> branches = listBranches(sessionId, userId);
        List<BranchMessageEntity> allMessages = repository.findAllMessagesBySession(sessionId, userId);

        Map<String, List<BranchMessageEntity>> childrenMap = new HashMap<>();
        List<BranchMessageEntity> roots = new ArrayList<>();

        for (BranchMessageEntity msg : allMessages) {
            if (msg.parentId() == null || msg.parentId().isBlank()) {
                roots.add(msg);
            } else {
                childrenMap
                        .computeIfAbsent(msg.parentId(), k -> new ArrayList<>())
                        .add(msg);
            }
        }

        Map<String, String> branchLabels = new HashMap<>();
        for (BranchSummary b : branches) {
            branchLabels.put(b.branchId(), b.branchLabel());
        }

        List<TreeNode> rootNodes = roots.stream()
                .map(r -> buildTreeNode(r, childrenMap, branchLabels))
                .toList();

        String currentActive = (activeBranchId != null && !activeBranchId.isBlank())
                ? activeBranchId
                : (branches.isEmpty() ? "main" : branches.getFirst().branchId());

        return new ConversationTree(sessionId, currentActive, branches, rootNodes);
    }

    private TreeNode buildTreeNode(
            BranchMessageEntity entity,
            Map<String, List<BranchMessageEntity>> childrenMap,
            Map<String, String> branchLabels) {
        List<BranchMessageEntity> childrenEntities = childrenMap.getOrDefault(entity.id(), List.of());
        List<TreeNode> childrenNodes = childrenEntities.stream()
                .map(c -> buildTreeNode(c, childrenMap, branchLabels))
                .toList();

        return new TreeNode(
                entity.id(),
                entity.branchId(),
                branchLabels.getOrDefault(entity.branchId(), "未命名分支"),
                entity.parentId(),
                entity.role(),
                entity.content(),
                entity.createdAt(),
                childrenNodes);
    }

    public BranchDiff diff(String sessionId, String userId, String branchA, String branchB) {
        List<BranchMessageEntity> msgsA = repository.findMessagesByBranch(sessionId, branchA, userId);
        List<BranchMessageEntity> msgsB = repository.findMessagesByBranch(sessionId, branchB, userId);

        Set<String> contentsInA = new HashSet<>();
        for (BranchMessageEntity a : msgsA) {
            contentsInA.add(a.content());
        }

        Set<String> contentsInB = new HashSet<>();
        for (BranchMessageEntity b : msgsB) {
            contentsInB.add(b.content());
        }

        String commonAncestorId = null;
        for (int i = 0; i < Math.min(msgsA.size(), msgsB.size()); i++) {
            if (msgsA.get(i).content().equals(msgsB.get(i).content())) {
                commonAncestorId = msgsA.get(i).id();
            } else {
                break;
            }
        }

        List<DiffMessageItem> diffA = msgsA.stream()
                .map(m -> new DiffMessageItem(
                        m.id(),
                        m.branchId(),
                        m.role(),
                        m.content(),
                        m.createdAt(),
                        contentsInB.contains(m.content()) ? "UNCHANGED" : "MODIFIED"))
                .toList();

        List<DiffMessageItem> diffB = msgsB.stream()
                .map(m -> new DiffMessageItem(
                        m.id(),
                        m.branchId(),
                        m.role(),
                        m.content(),
                        m.createdAt(),
                        contentsInA.contains(m.content()) ? "UNCHANGED" : "ADDED"))
                .toList();

        return new BranchDiff(branchA, branchB, commonAncestorId, diffA, diffB);
    }

    public MergeResult merge(String sessionId, String userId, String sourceBranchId, String targetBranchId) {
        if (sourceBranchId.equals(targetBranchId)) {
            return new MergeResult(false, targetBranchId, 0, "源分支与目标分支相同，无需合并");
        }

        List<BranchMessageEntity> sourceMsgs = repository.findMessagesByBranch(sessionId, sourceBranchId, userId);
        List<BranchMessageEntity> targetMsgs = repository.findMessagesByBranch(sessionId, targetBranchId, userId);

        Set<String> targetContents = new HashSet<>();
        for (BranchMessageEntity t : targetMsgs) {
            targetContents.add(t.content());
        }

        String lastTargetParentId =
                targetMsgs.isEmpty() ? null : targetMsgs.getLast().id();
        int mergedCount = 0;

        for (BranchMessageEntity src : sourceMsgs) {
            if (!targetContents.contains(src.content())) {
                BranchMessageEntity inserted = repository.insertMessage(
                        sessionId, userId, targetBranchId, lastTargetParentId, src.role(), src.content());
                lastTargetParentId = inserted.id();
                mergedCount++;
            }
        }

        log.info("合并分支完成: source={}, target={}, mergedCount={}", sourceBranchId, targetBranchId, mergedCount);
        return new MergeResult(true, targetBranchId, mergedCount, "成功将 " + mergedCount + " 条增量消息合并到目标分支");
    }
}
