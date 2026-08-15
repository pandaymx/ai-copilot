package xyz.ppmblszdp.ai.rag.sync;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import xyz.ppmblszdp.ai.rag.dto.ConflictPolicy;
import xyz.ppmblszdp.ai.rag.reader.SourceType;
import xyz.ppmblszdp.ai.rag.service.RagIngestionService;
import xyz.ppmblszdp.ai.rag.sync.connector.KnowledgeConnector;
import xyz.ppmblszdp.ai.rag.sync.dto.*;
import xyz.ppmblszdp.ai.rag.sync.service.KnowledgeSyncService;

class KnowledgeSyncServiceTest {

    private RagIngestionService ragIngestionService;
    private KnowledgeConnector mockConnector;
    private KnowledgeSyncService syncService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ragIngestionService = mock(RagIngestionService.class);
        mockConnector = mock(KnowledgeConnector.class);
        when(mockConnector.supports("GITHUB")).thenReturn(true);

        when(ragIngestionService.ingest(
                        any(SourceType.class), anyString(), anyString(), anyString(), any(ConflictPolicy.class), any()))
                .thenReturn(new RagIngestionService.IngestResult(3, 0));

        ObjectProvider<List<KnowledgeConnector>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenReturn(List.of(mockConnector));

        syncService = new KnowledgeSyncService(ragIngestionService, provider);
    }

    @Test
    void createSource_masksSensitiveTokens() {
        CreateSourceReq req = new CreateSourceReq(
                "My Private Repo",
                "GITHUB",
                Map.of("repo", "org/repo", "token", "ghp_1234567890abcdef"),
                "0 0 * * * ?",
                true);

        KnowledgeSourceDto created = syncService.createSource(req);
        assertNotNull(created.id());
        assertEquals("My Private Repo", created.name());

        // 校验 Token 脱敏
        String maskedToken = (String) created.config().get("token");
        assertTrue(maskedToken.contains("****"));
        assertFalse(maskedToken.contains("1234567890"));
    }

    @Test
    void syncSource_initialSync_ingestsAllDocuments() throws Exception {
        RemoteKnowledgeDoc doc1 =
                RemoteKnowledgeDoc.of("https://github.com/org/repo/blob/main/a.md", "Doc A", "Content A", "hash-aaa");
        RemoteKnowledgeDoc doc2 =
                RemoteKnowledgeDoc.of("https://github.com/org/repo/blob/main/b.md", "Doc B", "Content B", "hash-bbb");
        when(mockConnector.fetchDocuments(any())).thenReturn(List.of(doc1, doc2));

        CreateSourceReq req =
                new CreateSourceReq("Test Source", "GITHUB", Map.of("repo", "org/repo"), "0 0 * * * ?", true);
        KnowledgeSourceDto source = syncService.createSource(req);

        KnowledgeSyncResultDto result = syncService.syncSource(source.id(), false);

        assertTrue(result.success());
        assertEquals(2, result.totalRemoteDocs());
        assertEquals(2, result.addedCount());
        assertEquals(0, result.skippedCount());
        assertEquals(0, result.deletedCount());

        verify(ragIngestionService, times(2))
                .ingest(
                        eq(SourceType.TEXT),
                        anyString(),
                        anyString(),
                        eq("system"),
                        eq(ConflictPolicy.OVERWRITE),
                        any());
    }

    @Test
    void syncSource_incrementalSync_skipsUnchangedAndCleansDeleted() throws Exception {
        // 第 1 轮拉取: doc1, doc2
        RemoteKnowledgeDoc doc1 =
                RemoteKnowledgeDoc.of("https://github.com/org/repo/blob/main/a.md", "Doc A", "Content A", "hash-aaa");
        RemoteKnowledgeDoc doc2 =
                RemoteKnowledgeDoc.of("https://github.com/org/repo/blob/main/b.md", "Doc B", "Content B", "hash-bbb");
        when(mockConnector.fetchDocuments(any())).thenReturn(List.of(doc1, doc2));

        CreateSourceReq req =
                new CreateSourceReq("Incremental Source", "GITHUB", Map.of("repo", "org/repo"), "0 0 * * * ?", true);
        KnowledgeSourceDto source = syncService.createSource(req);
        syncService.syncSource(source.id(), false);

        // 第 2 轮拉取: doc1 未变(hash-aaa), doc2 被删除, doc3 新增(hash-ccc)
        RemoteKnowledgeDoc doc3 =
                RemoteKnowledgeDoc.of("https://github.com/org/repo/blob/main/c.md", "Doc C", "Content C", "hash-ccc");
        when(mockConnector.fetchDocuments(any())).thenReturn(List.of(doc1, doc3));

        KnowledgeSyncResultDto result2 = syncService.syncSource(source.id(), false);

        assertTrue(result2.success());
        assertEquals(2, result2.totalRemoteDocs());
        assertEquals(1, result2.addedCount()); // doc3 新增
        assertEquals(1, result2.skippedCount()); // doc1 跳过 (零 Token 消耗)
        assertEquals(1, result2.deletedCount()); // doc2 远端已删除，本地清理

        // 验证清理了 doc2
        verify(ragIngestionService, times(1))
                .deleteBySourceAndUser(eq("https://github.com/org/repo/blob/main/b.md"), eq("TEXT"), eq("system"));
    }

    @Test
    void deleteSource_cascadesCleanupToVectorStore() throws Exception {
        RemoteKnowledgeDoc doc1 =
                RemoteKnowledgeDoc.of("https://github.com/org/repo/blob/main/a.md", "Doc A", "Content A", "hash-aaa");
        when(mockConnector.fetchDocuments(any())).thenReturn(List.of(doc1));

        CreateSourceReq req =
                new CreateSourceReq("To Delete", "GITHUB", Map.of("repo", "org/repo"), "0 0 * * * ?", true);
        KnowledgeSourceDto source = syncService.createSource(req);
        syncService.syncSource(source.id(), false);

        boolean deleted = syncService.deleteSource(source.id());
        assertTrue(deleted);
        assertFalse(syncService.getSource(source.id()).isPresent());
    }
}
