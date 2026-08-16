package xyz.ppmblszdp.ai.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.repository.SessionParticipant;
import xyz.ppmblszdp.ai.repository.SessionParticipantRepository;

/**
 * 共享会话权限断言服务。
 *
 * <p>所有涉及共享会话的修改/删除/邀请操作，<b>必须</b>在 Service 层调用本服务做二次断言，
 * 绝不能仅依赖 SQL 查询的过滤（见安全越权修正点）。
 */
@Service
public class ParticipantAuthService {

    private final SessionParticipantRepository participantRepository;

    public ParticipantAuthService(SessionParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    /** 是否为该会话的参与者（含所有者）。 */
    public Mono<Boolean> isParticipant(String sessionId, String userId) {
        return roleOf(sessionId, userId).map(role -> role != null);
    }

    /** 返回角色，非参与者为 null。 */
    public Mono<SessionParticipant.Role> roleOf(String sessionId, String userId) {
        return participantRepository.roleOf(sessionId, userId);
    }

    /**
     * 校验用户是否具备给定最低角色，否则抛出 {@link CollabAccessDeniedException}。
     *
     * @param minRole 要求的最低角色（如 EDITOR 表示允许 EDITOR/VIEWER 之上的角色）。
     */
    public Mono<Void> requireRole(String sessionId, String userId, SessionParticipant.Role minRole) {
        return roleOf(sessionId, userId).flatMap(role -> {
            if (role == null) {
                return Mono.error(new CollabAccessDeniedException("用户不是该会话的参与者: " + userId));
            }
            if (!role.atLeast(minRole)) {
                return Mono.error(new CollabAccessDeniedException("权限不足: 需要 " + minRole + " 但当前为 " + role));
            }
            return Mono.empty();
        });
    }

    /** 是否为所有者（仅 OWNER 可管理协作者）。 */
    public Mono<Boolean> isOwner(String sessionId, String userId) {
        return roleOf(sessionId, userId).map(role -> role == SessionParticipant.Role.OWNER);
    }
}
