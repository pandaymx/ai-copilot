package xyz.ppmblszdp.ai.rag.chunker;

import java.util.List;
import java.util.function.Function;
import org.springframework.ai.document.Document;

/**
 * RAG 文本切片策略接口（回应风险2：分词器匹配度）。
 *
 * <p>不同 Embedding 模型（BGE / Text-Embedding-3 / Qwen-Embedding 等）的 Token 计算规则差异较大；
 * 通过策略接口预留多实现替换点，默认实现为 {@link TokenBasedRagTextSplitter}（基于 TokenTextSplitter
 * + 自实现 overlap 包装），备选 {@link CharacterBasedRagTextSplitter} 按字符/段落维度切片。
 */
@FunctionalInterface
public interface RagTextSplitter extends Function<List<Document>, List<Document>> {}
