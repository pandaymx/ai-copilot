package xyz.ppmblszdp.ai.rag.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.rag.graph.controller.KnowledgeGraphController;
import xyz.ppmblszdp.ai.rag.graph.controller.KnowledgeGraphController.GraphExtractRequest;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeEntity;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeGraphDto;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeGraphDto.GraphStatsDto;
import xyz.ppmblszdp.ai.rag.graph.repository.KnowledgeGraphRepository;
import xyz.ppmblszdp.ai.rag.graph.service.GraphRagService;

class KnowledgeGraphControllerTest {

    private KnowledgeGraphRepository repository;
    private GraphRagService graphRagService;
    private KnowledgeGraphController controller;

    @BeforeEach
    void setUp() {
        repository = new KnowledgeGraphRepository();
        graphRagService = mock(GraphRagService.class);
        AuthProperties auth = new AuthProperties("dev", "X-User-Id", java.util.Set.of("admin"));

        controller = new KnowledgeGraphController(repository, graphRagService, auth);
    }

    @Test
    @DisplayName("GET /api/rag/graph 返回全量图谱及统计")
    void testGetGraph() {
        ResponseEntity<KnowledgeGraphDto> resp = controller.getGraph(null, null, null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().nodes()).isNotEmpty();
    }

    @Test
    @DisplayName("GET /api/rag/graph/subgraph 接收 seeds 并返回子图")
    void testGetSubgraph() {
        ResponseEntity<KnowledgeGraphDto> resp = controller.getSubgraph("Spring AI", null, 2, 50, null, null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().nodes().stream().map(KnowledgeEntity::name)).contains("Spring AI");
    }

    @Test
    @DisplayName("GET /api/rag/graph/stats 返回统计指标")
    void testGetStats() {
        ResponseEntity<GraphStatsDto> resp = controller.getStats(null, null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().totalNodes()).isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("POST /api/rag/graph/extract 提取文本并返回图谱数据")
    void testExtract() {
        when(graphRagService.extractAndIndex(any(), any(), any()))
                .thenReturn(new KnowledgeGraphDto(
                        List.of(new KnowledgeEntity("e1", "u1", "d1", "TestEnt", "CONCEPT", "Desc", 1.0)),
                        List.of(),
                        null));

        GraphExtractRequest req = new GraphExtractRequest("测试文本", "doc-1", "user-1");
        ResponseEntity<KnowledgeGraphDto> resp = controller.extract(req, null);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().nodes()).hasSize(1);
    }
}
