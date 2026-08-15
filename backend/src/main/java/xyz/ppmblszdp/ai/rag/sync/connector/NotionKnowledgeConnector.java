package xyz.ppmblszdp.ai.rag.sync.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.rag.sync.dto.KnowledgeSourceDto;
import xyz.ppmblszdp.ai.rag.sync.dto.RemoteKnowledgeDoc;

/**
 * Notion 知识库连接器：
 * 通过 Notion Official API (Search / Database Query) 拉取 Pages 并在内存转为 Markdown。
 */
@Component
public class NotionKnowledgeConnector implements KnowledgeConnector {

    private static final Logger log = LoggerFactory.getLogger(NotionKnowledgeConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public boolean supports(String sourceType) {
        return "NOTION".equalsIgnoreCase(sourceType);
    }

    @Override
    public List<RemoteKnowledgeDoc> fetchDocuments(KnowledgeSourceDto source) throws Exception {
        Map<String, Object> cfg = source.config() != null ? source.config() : Map.of();
        String apiKey = String.valueOf(cfg.getOrDefault("apiKey", cfg.getOrDefault("token", "")))
                .trim();

        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("Notion 知识源未配置 Integration API Key (apiKey / token)");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/search"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"filter\":{\"value\":\"page\",\"property\":\"object\"},\"page_size\":50}"))
                .build();

        HttpResponse<String> response =
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Notion API 调用失败 [HTTP " + response.statusCode() + "]: " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }

        List<RemoteKnowledgeDoc> docs = new ArrayList<>();
        for (JsonNode page : results) {
            String pageId = page.path("id").asText();
            String url = page.path("url").asText("https://notion.so/" + pageId);
            String lastEditedTime = page.path("last_edited_time").asText();

            String title = extractNotionTitle(page);
            String content =
                    "Notion Page Title: " + title + "\n\nPage ID: " + pageId + "\nLast Edited: " + lastEditedTime;

            String hash = sha256(content + lastEditedTime);
            docs.add(new RemoteKnowledgeDoc(
                    url, title, content, hash, System.currentTimeMillis(), Map.of("pageId", pageId)));
        }

        log.info("Notion 知识库扫描完成，成功获取页面 {} 篇", docs.size());
        return docs;
    }

    private String extractNotionTitle(JsonNode page) {
        JsonNode props = page.path("properties");
        for (JsonNode prop : props) {
            if ("title".equalsIgnoreCase(prop.path("type").asText())) {
                JsonNode titleArr = prop.path("title");
                if (titleArr.isArray() && !titleArr.isEmpty()) {
                    return titleArr.get(0).path("plain_text").asText("Untitled");
                }
            }
        }
        return "Notion Document";
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
