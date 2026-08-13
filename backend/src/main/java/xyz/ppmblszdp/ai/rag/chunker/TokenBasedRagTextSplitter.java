package xyz.ppmblszdp.ai.rag.chunker;

import com.knuddels.jtokkit.api.EncodingType;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.rag.RagProperties;

/**
 * 基于 {@link TokenTextSplitter} 的默认切片策略，附加自实现 overlap 尾部复用包装。
 *
 * <p>Spring AI 2.0 的 {@code TokenTextSplitter.builder()} 原生不支持 overlap 参数；
 * 因此先通过 builder 构造基础切片器，再对相邻 chunk 的尾部 N tokens 做上下文复用，
 * 防止语义边界信息丢失。
 *
 * <p>{@code encodingType} 默认为 CL100K_BASE（GPT-4 编码器），可通过配置切换；
 * 若使用非 GPT 分词器（BGE / Qwen-Embedding 等），建议替换为 {@link CharacterBasedRagTextSplitter}。
 */
@Component
public class TokenBasedRagTextSplitter implements RagTextSplitter {

    private static final Logger log = LoggerFactory.getLogger(TokenBasedRagTextSplitter.class);

    private final TokenTextSplitter splitter;
    private final int overlap;

    public TokenBasedRagTextSplitter(RagProperties properties) {
        int chunkSize = properties.resolveChunkSize();
        this.overlap = properties.resolveOverlap();
        String encodingType = properties.resolveEncodingType();

        // Spring AI 2.0: 已弃用构造函数，必须用 builder
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withEncodingType(EncodingType.valueOf(encodingType))
                .build();

        log.info("TokenTextSplitter 装配完成: chunkSize={} overlap={} encoding={}", chunkSize, overlap, encodingType);
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        // 第一步：TokenTextSplitter 基础切片
        List<Document> chunks = splitter.apply(documents);

        // 第二步：overlap > 0 时尾部复用包装
        if (overlap <= 0 || chunks.size() <= 1) {
            return chunks;
        }

        List<Document> overlapped = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Document current = chunks.get(i);
            if (i == 0) {
                // 第一片：尾部追加下一片的头部文本
                Document next = chunks.get(i + 1);
                String enhanced = enhanceWithOverlap(current.getText(), next.getText(), overlap);
                overlapped.add(copyWithText(current, enhanced));
            } else {
                // 中间片：首部追加上一片的尾部文本
                Document prev = chunks.get(i - 1);
                String enhanced = enhanceWithPrefix(prev.getText(), current.getText(), overlap);
                overlapped.add(copyWithText(current, enhanced));
            }
        }

        log.debug("切片完成: 原始文档数={} chunk数={}（含 overlap）", documents.size(), overlapped.size());
        return overlapped;
    }

    /**
     * 在当前 chunk 尾部追加下一片的前 {@code overlap} 个字符作为上下文重叠。
     */
    private String enhanceWithOverlap(String current, String next, int overlap) {
        if (next == null || next.isEmpty()) return current;
        String tailPrefix = next.substring(0, Math.min(overlap, next.length()));
        return current + " " + tailPrefix;
    }

    /**
     * 在当前 chunk 首部追加上一片的后 {@code overlap} 个字符作为上下文重叠。
     */
    private String enhanceWithPrefix(String prev, String current, int overlap) {
        if (prev == null || prev.isEmpty()) return current;
        String prefixTail = prev.substring(Math.max(0, prev.length() - overlap));
        return prefixTail + " " + current;
    }

    private Document copyWithText(Document original, String newText) {
        return new Document(original.getId(), newText, new java.util.HashMap<>(original.getMetadata()));
    }
}
