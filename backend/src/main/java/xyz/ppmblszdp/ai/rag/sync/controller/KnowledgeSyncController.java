package xyz.ppmblszdp.ai.rag.sync.controller;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.ppmblszdp.ai.rag.sync.dto.*;
import xyz.ppmblszdp.ai.rag.sync.service.KnowledgeSyncService;

/**
 * 知识源与自动增量同步 REST API 控制器。
 */
@RestController
@RequestMapping("/api/rag/sync/sources")
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class KnowledgeSyncController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSyncController.class);

    private final KnowledgeSyncService syncService;

    public KnowledgeSyncController(KnowledgeSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * 查询已注册的知识源列表（敏感 Token 已做脱敏）。
     */
    @GetMapping
    public ResponseEntity<List<KnowledgeSourceDto>> listSources() {
        return ResponseEntity.ok(syncService.listSources());
    }

    /**
     * 查询单个知识源。
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getSource(@PathVariable String id) {
        return syncService.getSource(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(404)
                .body(null));
    }

    /**
     * 创建新知识源。
     */
    @PostMapping
    public ResponseEntity<KnowledgeSourceDto> createSource(@RequestBody CreateSourceReq req) {
        log.info("创建知识源请求: name={}, type={}", req.name(), req.sourceType());
        KnowledgeSourceDto created = syncService.createSource(req);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新知识源配置。
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateSource(@PathVariable String id, @RequestBody UpdateSourceReq req) {
        log.info("更新知识源配置请求: id={}, name={}", id, req.name());
        return syncService.updateSource(id, req).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(404)
                .body(null));
    }

    /**
     * 删除知识源及其关联数据。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSource(@PathVariable String id) {
        log.info("删除知识源请求: id={}", id);
        boolean deleted = syncService.deleteSource(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true, "message", "知识源已删除"));
        }
        return ResponseEntity.status(404).body(Map.of("success", false, "error", "未找到指定知识源"));
    }

    /**
     * 手动触发指定知识源的增量同步。
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<KnowledgeSyncResultDto> syncSource(
            @PathVariable String id, @RequestParam(defaultValue = "false") boolean force) {
        log.info("手动触发知识源同步: id={}, force={}", id, force);
        KnowledgeSyncResultDto result = syncService.syncSource(id, force);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定知识源的同步历史日志。
     */
    @GetMapping("/{id}/logs")
    public ResponseEntity<List<KnowledgeSyncResultDto>> getLogs(@PathVariable String id) {
        return ResponseEntity.ok(syncService.getSyncLogs(id));
    }
}
