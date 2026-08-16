package xyz.ppmblszdp.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.MediaDto;
import xyz.ppmblszdp.ai.dto.SessionDto;
import xyz.ppmblszdp.ai.repository.SessionParticipant;
import xyz.ppmblszdp.ai.repository.SessionParticipantRepository;
import xyz.ppmblszdp.ai.repository.SessionRepository;

/**
 * 会话服务：整合 PostgreSQL 会话元数据表与 ChatMemory 消息上下文。
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final ObjectProvider<ChatMemory> chatMemoryProvider;

    public SessionService(
            SessionRepository sessionRepository,
            SessionParticipantRepository participantRepository,
            ObjectProvider<ChatMemory> chatMemoryProvider) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.chatMemoryProvider = chatMemoryProvider;
    }

    /** 查询指定用户的所有历史会话列表 */
    public List<SessionDto> getAllSessions(String userId) {
        return sessionRepository.findAllByUserId(userId);
    }

    /** 按用户归属查询会话元数据（用于跨用户隔离校验），不存在返回 empty */
    public Optional<SessionDto> findSession(String id, String userId) {
        return sessionRepository.findByIdAndUserId(id, userId);
    }

    /** 查询指定会话的完整历史（含元数据与消息数组） */
    public Optional<SessionDto.SessionDetail> getSessionDetail(String id, String userId) {
        Optional<SessionDto> metaOpt = sessionRepository.findByIdAndUserId(id, userId);
        ChatMemory memory = chatMemoryProvider.getIfAvailable();

        List<SessionDto.MessageItem> messageItems = new ArrayList<>();
        if (memory != null) {
            try {
                List<Message> rawMessages = memory.get(id);
                int index = 0;
                for (Message m : rawMessages) {
                    // 过滤系统全局 prompt，仅将用户与助手对话暴露给前端
                    if (m.getMessageType() == MessageType.SYSTEM) {
                        continue;
                    }
                    String role =
                            switch (m.getMessageType()) {
                                case USER -> "user";
                                case ASSISTANT -> "assistant";
                                default -> "user";
                            };
                    List<MediaDto> mediaDtos = extractMediaDtos(m);
                    String msgId = "db-msg-" + id + "-" + (++index);
                    messageItems.add(new SessionDto.MessageItem(msgId, role, m.getText(), mediaDtos));
                }
            } catch (Exception ex) {
                log.warn("从 ChatMemory 读取会话 '{}' 消息失败: {}", id, ex.getMessage());
            }
        }

        if (metaOpt.isPresent()) {
            SessionDto meta = metaOpt.get();
            return Optional.of(new SessionDto.SessionDetail(
                    meta.id(),
                    meta.title(),
                    meta.updatedAt(),
                    meta.isDefaultTitle(),
                    meta.parentSessionId(),
                    meta.inheritedContextJson(),
                    messageItems));
        } else if (!messageItems.isEmpty()) {
            // 如果元数据表中未命中，但 ChatMemory 中有记录，构造兜底元数据
            return Optional.of(new SessionDto.SessionDetail(
                    id, "历史会话", System.currentTimeMillis(), false, null, null, messageItems));
        }
        return Optional.empty();
    }

    /** 新建/置顶/更新会话元数据（绑定用户） */
    public void recordSession(String id, String userId, String title, boolean isDefaultTitle) {
        sessionRepository.upsertSession(id, userId, title, System.currentTimeMillis(), isDefaultTitle);
        participantRepository.ensureOwner(id, userId).subscribe();
    }

    /** 写入带上下文继承关系的会话元数据 */
    public void recordSessionWithInheritance(
            String id,
            String userId,
            String title,
            boolean isDefaultTitle,
            String parentSessionId,
            String inheritedContextJson) {
        sessionRepository.upsertSessionWithInheritance(
                id, userId, title, System.currentTimeMillis(), isDefaultTitle, parentSessionId, inheritedContextJson);
        participantRepository.ensureOwner(id, userId).subscribe();
    }

    /** 查询指定用户在会话中的协作角色（非参与者为空）。 */
    public java.util.Optional<SessionParticipant.Role> getParticipantRole(String id, String userId) {
        return participantRepository.roleOf(id, userId).blockOptional();
    }

    /** 列出会话全部参与者（含所有者）。 */
    public List<SessionDto.Participant> listParticipants(String id) {
        return participantRepository
                .listBySession(id)
                .blockOptional()
                .map(list -> list.stream()
                        .map(p ->
                                new SessionDto.Participant(p.userId(), p.role().name()))
                        .toList())
                .orElse(java.util.List.of());
    }

    /** 发送消息时刷新会话时间戳（绑定用户） */
    public void touchSession(String id, String userId, String fallbackTitle) {
        sessionRepository.touchSession(id, userId, fallbackTitle, System.currentTimeMillis());
    }

    /** 重命名会话标题（按用户隔离） */
    public boolean renameSession(String id, String userId, String newTitle) {
        Optional<SessionDto> existing = sessionRepository.findByIdAndUserId(id, userId);
        if (existing.isEmpty()) {
            sessionRepository.upsertSession(id, userId, newTitle, System.currentTimeMillis(), false);
        } else {
            sessionRepository.updateTitle(id, userId, newTitle, false);
        }
        return true;
    }

    /**
     * 删除指定用户的会话，返回是否成功删除（false 表示会话不存在或不属于该用户）。
     * 跨用户操作返回 false，由 Controller 转换为 404。
     */
    public boolean deleteSession(String id, String userId) {
        int affected = sessionRepository.deleteByIdAndUserId(id, userId);
        if (affected <= 0) {
            return false;
        }
        ChatMemory memory = chatMemoryProvider.getIfAvailable();
        if (memory != null) {
            try {
                memory.clear(id);
            } catch (Exception ex) {
                log.warn("清除会话 '{}' ChatMemory 失败: {}", id, ex.getMessage());
            }
        }
        return true;
    }

    private List<MediaDto> extractMediaDtos(Message m) {
        if (!(m instanceof org.springframework.ai.chat.messages.UserMessage userMsg)) {
            return null;
        }
        List<org.springframework.ai.content.Media> mediaList = userMsg.getMedia();
        if (mediaList == null || mediaList.isEmpty()) {
            return null;
        }
        List<MediaDto> dtos = new ArrayList<>();
        for (org.springframework.ai.content.Media media : mediaList) {
            try {
                String mimeType =
                        (media.getMimeType() != null) ? media.getMimeType().toString() : "image/png";
                String data = null;
                Object rawData = media.getData();
                if (rawData instanceof byte[] bytes) {
                    data = "data:" + mimeType + ";base64,"
                            + java.util.Base64.getEncoder().encodeToString(bytes);
                } else if (rawData instanceof String str) {
                    data = str.startsWith("data:") ? str : "data:" + mimeType + ";base64," + str;
                }
                if (data != null) {
                    dtos.add(new MediaDto(mimeType, data));
                }
            } catch (Exception e) {
                log.warn("无法从 UserMessage 提取 Media: {}", e.getMessage());
            }
        }
        return dtos.isEmpty() ? null : dtos;
    }
}
