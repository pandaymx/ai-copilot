package xyz.ppmblszdp.ai.mcp;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.memory.SafeVectorStore;

/**
 * 将用户私有 RAG 知识库与向量文档适配为 MCP Resources。
 */
@Component
public class RagResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(RagResourceProvider.class);
    private final VectorStore vectorStore;

    public RagResourceProvider(@Qualifier("ragVectorStore") ObjectProvider<VectorStore> ragVectorStore) {
        VectorStore vs = ragVectorStore.getIfAvailable();
        this.vectorStore = (vs instanceof SafeVectorStore) ? vs : new SafeVectorStore(vs);
    }

    public List<McpProtocolDto.McpResourceDefinition> listResources(String userId) {
        List<McpProtocolDto.McpResourceDefinition> list = new ArrayList<>();
        list.add(new McpProtocolDto.McpResourceDefinition(
                "rag://user/" + userId + "/knowledge", "用户私有知识库文档集合", "包含用户上传与录入的所有 RAG 向量知识文档", "text/markdown"));
        return list;
    }

    public McpProtocolDto.McpResourceContent readResource(String userId, String uri) {
        try {
            FilterExpressionBuilder feb = new FilterExpressionBuilder();
            Expression filter =
                    feb.eq("userId", userId != null ? userId : "default").build();
            SearchRequest request =
                    SearchRequest.builder().topK(10).filterExpression(filter).build();
            List<Document> docs = vectorStore.similaritySearch(request);

            StringBuilder sb = new StringBuilder();
            sb.append("# AI Copilot 用户知识库资源: ").append(uri).append("\n\n");
            if (docs.isEmpty()) {
                sb.append("（暂无已索引的知识文档）\n");
            } else {
                for (int i = 0; i < docs.size(); i++) {
                    Document doc = docs.get(i);
                    sb.append("## 文档片段 ").append(i + 1).append("\n");
                    sb.append(doc.getText()).append("\n\n");
                }
            }
            return new McpProtocolDto.McpResourceContent(uri, "text/markdown", sb.toString());
        } catch (Exception e) {
            log.warn("读取 MCP RAG 资源失败: {}", e.getMessage());
            return new McpProtocolDto.McpResourceContent(uri, "text/plain", "获取资源失败: " + e.getMessage());
        }
    }
}
