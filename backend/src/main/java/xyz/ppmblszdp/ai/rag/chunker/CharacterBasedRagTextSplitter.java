package xyz.ppmblszdp.ai.rag.chunker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import xyz.ppmblszdp.ai.rag.RagProperties;

/**
 * 基于字符/段落维度的备选切片策略。
 *
 * <p>适用于非 GPT 分词器的 Embedding 模型（BGE / Text-Embedding-3 / Qwen-Embedding 等），
 * 按字符长度分片，以双换行（段落边界）为自然切分点，辅以 overlap 防止边界丢失。
 * 当 {@code TokenTextSplitter} 的 encoding 与模型不匹配时，可切换至此实现。
 */
public class CharacterBasedRagTextSplitter implements RagTextSplitter {

    private static final Logger log = LoggerFactory.getLogger(CharacterBasedRagTextSplitter.class);

    private final int chunkSize;
    private final int overlap;

    public CharacterBasedRagTextSplitter(RagProperties properties) {
        // 字符维度：1 token ≈ 2 中文字符 / 4 英文字符，此处按约 2.5× 折中
        this.chunkSize = properties.resolveChunkSize() * 2;
        this.overlap = properties.resolveOverlap() * 2;
        log.info("CharacterBasedRagTextSplitter 装配完成: charChunkSize={} charOverlap={}", chunkSize, overlap);
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<Document> chunks = new ArrayList<>();
        for (Document doc : documents) {
            chunks.addAll(splitDocument(doc));
        }
        log.debug("字符切片完成: 原始文档数={} chunk数={}", documents.size(), chunks.size());
        return chunks;
    }

    private List<Document> splitDocument(Document doc) {
        String text = doc.getText();
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // 优先按段落切分，再按字符窗口合并
        String[] paragraphs = text.split("\\R{2,}");
        List<Document> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int chunkIndex = 0;

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            if (buffer.length() + trimmed.length() < chunkSize) {
                if (buffer.length() > 0) buffer.append("\n\n");
                buffer.append(trimmed);
            } else {
                // 当前缓冲区满，提交 chunk
                if (buffer.length() > 0) {
                    result.add(createChunk(doc, buffer.toString(), chunkIndex++));
                }

                // 若单段落超过 chunkSize，强制按字符切分
                if (trimmed.length() >= chunkSize) {
                    int start = 0;
                    while (start < trimmed.length()) {
                        int end = Math.min(start + chunkSize, trimmed.length());
                        result.add(createChunk(doc, trimmed.substring(start, end), chunkIndex++));
                        start = end - (overlap > 0 ? overlap : 0);
                        if (start < 0) start = 0;
                    }
                    buffer.setLength(0);
                } else {
                    buffer = new StringBuilder(trimmed);
                }
            }
        }

        // 最后一 chunk
        if (buffer.length() > 0) {
            result.add(createChunk(doc, buffer.toString(), chunkIndex));
        }

        return result;
    }

    private Document createChunk(Document parent, String text, int index) {
        return new Document(parent.getId() + "_c" + index, text, new HashMap<>(parent.getMetadata()));
    }
}
