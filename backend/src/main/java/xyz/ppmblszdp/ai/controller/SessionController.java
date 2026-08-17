package xyz.ppmblszdp.ai.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.collab.CollabEvent;
import xyz.ppmblszdp.ai.collab.CollaborationBus;
import xyz.ppmblszdp.ai.dto.ConversationSummaryDto;
import xyz.ppmblszdp.ai.dto.SessionDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.repository.SessionParticipant;
import xyz.ppmblszdp.ai.repository.SessionParticipantRepository;
import xyz.ppmblszdp.ai.service.ConversationSummaryService;
import xyz.ppmblszdp.ai.service.ParticipantAuthService;
import xyz.ppmblszdp.ai.service.SessionService;

/**
 * 会话元数据、全量历史、结构化摘要与知识库沉淀 Controller。
 *
 * <p>所有端点均需经身份解析：从受信任 {@code X-User-Id} Header 读取用户身份，
 * 跨用户访问返回 404（不可区分「不存在」与「不属于你」）。
 */
@RestController
@RequestMapping("/api/chat/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final ConversationSummaryService summaryService;
    private final AuthProperties authProperties;
    private final ParticipantAuthService participantAuth;
    private final SessionParticipantRepository participantRepository;
    private final CollaborationBus collabBus;

    @org.springframework.beans.factory.annotation.Autowired
    public SessionController(
            SessionService sessionService,
            ConversationSummaryService summaryService,
            AuthProperties authProperties,
            ParticipantAuthService participantAuth,
            SessionParticipantRepository participantRepository,
            CollaborationBus collabBus) {
        this.sessionService = sessionService;
        this.summaryService = summaryService;
        this.authProperties = authProperties;
        this.participantAuth = participantAuth;
        this.participantRepository = participantRepository;
        this.collabBus = collabBus;
    }

    /** 兼容测试套件 2 参数构造器 */
    public SessionController(SessionService sessionService, AuthProperties authProperties) {
        this(sessionService, null, authProperties, null, null, null);
    }

    /** 从请求交换解析当前用户身份（严格模式缺 Header 抛 401） */
    private String resolveIdentity(ServerWebExchange exchange) {
        return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
    }

    /** 获取当前用户的历史会话列表（按用户隔离） */
    @GetMapping
    public ResponseEntity<List<SessionDto>> getSessions(ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        return ResponseEntity.ok(sessionService.getAllSessions(userId));
    }

    /** 获取单个会话的完整历史（元数据与消息），跨用户返回 404 */
    @GetMapping("/{id}")
    public ResponseEntity<SessionDto.SessionDetail> getSessionDetail(
            @PathVariable("id") String id, ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        return sessionService
                .getSessionDetail(id, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 生成/提炼会话结构化摘要 */
    @PostMapping("/{id}/summary")
    public Mono<ResponseEntity<ConversationSummaryDto>> generateSummary(
            @PathVariable("id") String id,
            @RequestBody(required = false) SummaryRequest request,
            ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        String provider = request != null ? request.provider() : null;
        String model = request != null ? request.model() : null;

        return summaryService
                .generateSummary(id, userId, provider, model)
                .map(ResponseEntity::ok)
                .onErrorResume(
                        IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(
                        Exception.class,
                        e -> Mono.just(ResponseEntity.internalServerError().build()));
    }

    /** 一键将当前会话/摘要沉淀至 RAG 个人知识库 */
    @PostMapping("/{id}/knowledge")
    public ResponseEntity<Map<String, Object>> saveToKnowledge(
            @PathVariable("id") String id, @RequestBody KnowledgeSaveRequest request, ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        if (request == null || request.summary() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "summary 不能为空"));
        }
        try {
            Map<String, Object> result =
                    summaryService.saveToKnowledgeBase(id, userId, request.summary(), request.customTitle());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 重命名会话（仅会话所有者），跨用户返回 404 */
    @PutMapping("/{id}/title")
    public ResponseEntity<Void> renameSession(
            @PathVariable("id") String id, @RequestBody SessionDto.RenameRequest request, ServerWebExchange exchange) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String userId = resolveIdentity(exchange);
        // 归属校验：不存在或不属于该用户 -> 404
        if (sessionService.findSession(id, userId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        sessionService.renameSession(id, userId, request.title().trim());
        return ResponseEntity.ok().build();
    }

    /** 删除会话（仅所有者），跨用户返回 404 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable("id") String id, ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        boolean deleted = sessionService.deleteSession(id, userId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    public record SummaryRequest(String provider, String model) {}

    public record KnowledgeSaveRequest(ConversationSummaryDto summary, String customTitle) {}

    // ---- 共享会话协作端点（修正点 5：所有写/删/邀请均经 Service 层 requireRole 二次断言）----

    /** 列出会话全部参与者（含所有者）。非参与者返回 404。 */
    @GetMapping("/{id}/participants")
    public Mono<ResponseEntity<List<SessionDto.Participant>>> listParticipants(
            @PathVariable("id") String id, ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        return participantAuth.isParticipant(id, userId).flatMap(isPart -> {
            if (!isPart) {
                return Mono.just(ResponseEntity.notFound().<List<SessionDto.Participant>>build());
            }
            return sessionService.listParticipants(id).map(ResponseEntity::ok);
        });
    }

    /** 查询当前用户在会话中的角色（frontend 决定只读态）。非参与者返回 404。 */
    @GetMapping("/{id}/my-role")
    public Mono<ResponseEntity<Map<String, String>>> myRole(@PathVariable("id") String id, ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        return participantAuth
                .roleOf(id, userId)
                .map(role -> ResponseEntity.ok(Map.of("role", role.name())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /** 邀请协作者（仅 OWNER）。role 仅允许 EDITOR / VIEWER。 */
    @PostMapping("/{id}/participants")
    public Mono<ResponseEntity<Map<String, String>>> inviteParticipant(
            @PathVariable("id") String id, @RequestBody InviteRequest request, ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        return participantAuth
                .requireRole(id, userId, SessionParticipant.Role.OWNER)
                .then(Mono.defer(() -> {
                    if (request == null
                            || request.targetUserId() == null
                            || request.targetUserId().isBlank()) {
                        return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "targetUserId 不能为空")));
                    }
                    SessionParticipant.Role role;
                    try {
                        role = SessionParticipant.Role.valueOf(
                                request.role() == null
                                        ? "VIEWER"
                                        : request.role().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "role 必须是 EDITOR 或 VIEWER")));
                    }
                    if (role == SessionParticipant.Role.OWNER) {
                        return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "不能分配 OWNER 角色")));
                    }
                    return participantRepository
                            .upsert(id, request.targetUserId(), role)
                            .then(Mono.fromCallable(() -> {
                                // 通知在线成员刷新成员列表
                                collabBus.broadcast(
                                        id,
                                        new CollabEvent.Presence(
                                                "presence",
                                                id,
                                                request.targetUserId(),
                                                role.name(),
                                                "invited",
                                                System.currentTimeMillis()));
                                return ResponseEntity.ok(Map.of("role", role.name()));
                            }));
                }));
    }

    /** 移除协作者（仅 OWNER，不能移除所有者）。 */
    @DeleteMapping("/{id}/participants/{targetUserId}")
    public Mono<ResponseEntity<Void>> removeParticipant(
            @PathVariable("id") String id,
            @PathVariable("targetUserId") String targetUserId,
            ServerWebExchange exchange) {
        String userId = resolveIdentity(exchange);
        return participantAuth
                .requireRole(id, userId, SessionParticipant.Role.OWNER)
                .then(participantRepository.remove(id, targetUserId))
                .then(Mono.fromCallable(() -> {
                    collabBus.broadcast(
                            id,
                            new CollabEvent.Presence(
                                    "presence", id, targetUserId, "VIEWER", "removed", System.currentTimeMillis()));
                    return ResponseEntity.ok().<Void>build();
                }));
    }

    public record InviteRequest(String targetUserId, String role) {}
}
