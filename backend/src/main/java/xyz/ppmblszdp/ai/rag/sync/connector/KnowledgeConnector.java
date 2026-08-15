package xyz.ppmblszdp.ai.rag.sync.connector;

import java.util.List;
import xyz.ppmblszdp.ai.rag.sync.dto.KnowledgeSourceDto;
import xyz.ppmblszdp.ai.rag.sync.dto.RemoteKnowledgeDoc;

/**
 * 外部知识数据源连接器契约接口。
 */
public interface KnowledgeConnector {

    /**
     * 判断当前连接器是否支持指定数据源类型。
     *
     * @param sourceType 数据源类型标识 (如 GITHUB, WEBSITE, SITEMAP, NOTION, CONFLUENCE)
     */
    boolean supports(String sourceType);

    /**
     * 从远端数据源拉取所有最新文档元数据与内容。
     *
     * @param source 知识源配置信息
     * @return 远端文档列表
     * @throws Exception 拉取异常或认证失败
     */
    List<RemoteKnowledgeDoc> fetchDocuments(KnowledgeSourceDto source) throws Exception;
}
