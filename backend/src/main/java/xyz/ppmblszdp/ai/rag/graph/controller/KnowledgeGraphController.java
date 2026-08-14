package xyz.ppmblszdp.ai.rag.graph.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeGraphDto;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeGraphDto.GraphStatsDto;
import xyz.ppmblszdp.ai.rag.graph.repository.KnowledgeGraphRepository;
import xyz.ppmblszdp.ai.rag.graph.service.GraphRagService;

/**
 * 知识图谱（Knowledge Graph / GraphRAG）管理与拓扑查询 REST 控制器。
 */
@RestController
@RequestMapping("/api/rag/graph")
@ConditionalOnProperty(prefix = "app.ai.rag", name = "enabled", havingValue = "true")
public class KnowledgeGraphController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphController.class);

    private final KnowledgeGraphRepository graphRepository;
    private final GraphRagService graphRagService;
    private final AuthProperties authProperties;

    public KnowledgeGraphController(
            KnowledgeGraphRepository graphRepository, GraphRagService graphRagService, AuthProperties authProperties) {
        this.graphRepository = graphRepository;
        this.graphRagService = graphRagService;
        this.authProperties = authProperties;
    }

    /**
     * 获取全量知识图谱或按文档过滤。
     */
    @GetMapping
    public ResponseEntity<KnowledgeGraphDto> getGraph(
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String userId,
            ServerWebExchange exchange) {
        String effectiveUser = resolveUser(userId, exchange);
        KnowledgeGraphDto graph = graphRepository.getFullGraph(effectiveUser, documentId);
        return ResponseEntity.ok(graph);
    }

    /**
     * 拓扑子图扩散查询（根据种子实体或查询文本执行 1~3 跳多跳扩散）。
     */
    @GetMapping("/subgraph")
    public ResponseEntity<KnowledgeGraphDto> getSubgraph(
            @RequestParam(required = false) String seeds,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "2") int maxHops,
            @RequestParam(defaultValue = "50") int maxNodes,
            @RequestParam(required = false) String userId,
            ServerWebExchange exchange) {
        String effectiveUser = resolveUser(userId, exchange);

        List<String> seedList = List.of();
        if (seeds != null && !seeds.isBlank()) {
            seedList = Arrays.stream(seeds.split("[,，;；]+"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        } else if (query != null && !query.isBlank()) {
            seedList = Arrays.stream(query.split("[\\s,，。！？!?;；、]+"))
                    .map(String::trim)
                    .filter(s -> s.length() >= 2)
                    .toList();
        }

        KnowledgeGraphDto subgraph = graphRepository.extractSubgraph(seedList, effectiveUser, maxHops, maxNodes);
        return ResponseEntity.ok(subgraph);
    }

    /**
     * 图谱大盘统计。
     */
    @GetMapping("/stats")
    public ResponseEntity<GraphStatsDto> getStats(
            @RequestParam(required = false) String userId, ServerWebExchange exchange) {
        String effectiveUser = resolveUser(userId, exchange);
        KnowledgeGraphDto graph = graphRepository.getFullGraph(effectiveUser, null);
        return ResponseEntity.ok(graph.stats());
    }

    /**
     * 对文本/文档执行三元组抽取与图谱构建。
     */
    @PostMapping("/extract")
    public ResponseEntity<KnowledgeGraphDto> extract(
            @RequestBody GraphExtractRequest request, ServerWebExchange exchange) {
        if (request == null || request.rawText() == null || request.rawText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String effectiveUser = resolveUser(request.userId(), exchange);
        KnowledgeGraphDto result =
                graphRagService.extractAndIndex(request.rawText(), request.documentId(), effectiveUser);
        return ResponseEntity.ok(result);
    }

    /**
     * 按文档删除图谱实体与边。
     */
    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> deleteDocumentGraph(
            @PathVariable String documentId,
            @RequestParam(required = false) String userId,
            ServerWebExchange exchange) {
        String effectiveUser = resolveUser(userId, exchange);
        int deleted = graphRepository.deleteByDocumentId(documentId, effectiveUser);
        return ResponseEntity.ok(Map.of("success", true, "deletedCount", deleted, "documentId", documentId));
    }

    public record GraphExtractRequest(String rawText, String documentId, String userId) {}

    private String resolveUser(String requestedUser, ServerWebExchange exchange) {
        if (exchange == null) {
            return (requestedUser != null && !requestedUser.isBlank())
                    ? requestedUser
                    : UserIdentityFilter.DEFAULT_USER_ID;
        }
        return UserIdentityFilter.resolveIdentity(exchange, requestedUser, authProperties);
    }
}
