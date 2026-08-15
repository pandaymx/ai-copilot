package xyz.ppmblszdp.ai.rag.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeEntity;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeGraphDto;
import xyz.ppmblszdp.ai.rag.graph.dto.KnowledgeRelation;
import xyz.ppmblszdp.ai.rag.graph.repository.KnowledgeGraphRepository;

class KnowledgeGraphRepositoryTest {

    private KnowledgeGraphRepository repository;

    @BeforeEach
    void setUp() {
        repository = new KnowledgeGraphRepository();
    }

    @Test
    @DisplayName("验证预置默认图谱实体与边初始化成功")
    void testDefaultGraphInitialization() {
        KnowledgeGraphDto graph = repository.getFullGraph(null, null);
        assertThat(graph.nodes()).isNotEmpty();
        assertThat(graph.edges()).isNotEmpty();
        assertThat(graph.stats().totalNodes()).isGreaterThanOrEqualTo(6);
        assertThat(graph.stats().totalEdges()).isGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("测试实体与关系边保存与查询")
    void testSaveEntityAndRelation() {
        KnowledgeEntity entA = new KnowledgeEntity("ent-a", "user-1", "doc-1", "Docker", "TECHNOLOGY", "容器化引擎", 1.0);
        KnowledgeEntity entB =
                new KnowledgeEntity("ent-b", "user-1", "doc-1", "Kubernetes", "TECHNOLOGY", "容器编排平台", 1.0);
        repository.saveEntity(entA);
        repository.saveEntity(entB);

        KnowledgeRelation rel = new KnowledgeRelation(
                "rel-ab", "user-1", "doc-1", "Kubernetes", "MANAGES", "Docker", "K8s 编排管理 Docker 容器", 1.0);
        repository.saveRelation(rel);

        List<KnowledgeEntity> entities = repository.listEntities("user-1", "doc-1");
        assertThat(entities).hasSize(2);

        List<KnowledgeRelation> relations = repository.listRelations("user-1", "doc-1");
        assertThat(relations).hasSize(1);
    }

    @Test
    @DisplayName("测试 2-Hop 多跳拓扑子图扩散遍历与环路检测")
    void testMultiHopSubgraphExtraction() {
        // 构建链条: A -> B -> C -> D -> A (环路)
        repository.saveEntity(new KnowledgeEntity("e1", "user-test", "doc-chain", "NodeA", "CONCEPT", "A节点", 1.0));
        repository.saveEntity(new KnowledgeEntity("e2", "user-test", "doc-chain", "NodeB", "CONCEPT", "B节点", 1.0));
        repository.saveEntity(new KnowledgeEntity("e3", "user-test", "doc-chain", "NodeC", "CONCEPT", "C节点", 1.0));
        repository.saveEntity(new KnowledgeEntity("e4", "user-test", "doc-chain", "NodeD", "CONCEPT", "D节点", 1.0));

        repository.saveRelation(
                new KnowledgeRelation("r1", "user-test", "doc-chain", "NodeA", "LEADS_TO", "NodeB", "A to B", 1.0));
        repository.saveRelation(
                new KnowledgeRelation("r2", "user-test", "doc-chain", "NodeB", "LEADS_TO", "NodeC", "B to C", 1.0));
        repository.saveRelation(
                new KnowledgeRelation("r3", "user-test", "doc-chain", "NodeC", "LEADS_TO", "NodeD", "C to D", 1.0));
        repository.saveRelation(
                new KnowledgeRelation("r4", "user-test", "doc-chain", "NodeD", "LEADS_TO", "NodeA", "D to A", 1.0));

        // 1-Hop 从 NodeA 扩散: 应包含 NodeA, NodeB, NodeD 以及 (A->B, D->A)
        KnowledgeGraphDto hop1 = repository.extractSubgraph(List.of("NodeA"), "user-test", 1, 50);
        assertThat(hop1.nodes().stream().map(n -> n.name())).contains("NodeA", "NodeB", "NodeD");

        // 2-Hop 从 NodeA 扩散: 应扩展到 NodeC
        KnowledgeGraphDto hop2 = repository.extractSubgraph(List.of("NodeA"), "user-test", 2, 50);
        assertThat(hop2.nodes().stream().map(n -> n.name())).contains("NodeA", "NodeB", "NodeC", "NodeD");
    }

    @Test
    @DisplayName("测试按文档级联删除图谱数据")
    void testDeleteByDocumentId() {
        repository.saveEntity(
                new KnowledgeEntity("e-tmp", "user-del", "doc-del-1", "TempConcept", "CONCEPT", "临时概念", 1.0));
        repository.saveRelation(new KnowledgeRelation(
                "r-tmp", "user-del", "doc-del-1", "TempConcept", "RELATES_TO", "Spring AI", "临时关联", 1.0));

        assertThat(repository.listEntities("user-del", "doc-del-1")).hasSize(1);
        int deleted = repository.deleteByDocumentId("doc-del-1", "user-del");
        assertThat(deleted).isGreaterThanOrEqualTo(2);
        assertThat(repository.listEntities("user-del", "doc-del-1")).isEmpty();
    }

    @Test
    @DisplayName("测试 GraphRAG 上下文文本序列化生成")
    void testBuildGraphContext() {
        String context = repository.buildGraphContext(List.of("Spring AI"), null, 2);
        assertThat(context).contains("【知识图谱关联实体与拓扑关系 (GraphRAG Context)】");
        assertThat(context).contains("Spring AI");
        assertThat(context).contains("PgVectorStore");
    }
}
