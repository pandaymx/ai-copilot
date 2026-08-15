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
 * Confluence 知识库连接器：
 * 通过 Atlassian Confluence REST API 拉取 Space / Content 页面。
 */
@Component
public class ConfluenceKnowledgeConnector implements KnowledgeConnector {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceKnowledgeConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public boolean supports(String sourceType) {
        return "CONFLUENCE".equalsIgnoreCase(sourceType);
    }

    @Override
    public List<RemoteKnowledgeDoc> fetchDocuments(KnowledgeSourceDto source) throws Exception {
        Map<String, Object> cfg = source.config() != null ? source.config() : Map.of();

        String baseUrl = String.valueOf(cfg.getOrDefault("baseUrl", cfg.getOrDefault("url", "")))
                .trim()
                .replaceAll("/$", "");
        String spaceKey = String.valueOf(cfg.getOrDefault("spaceKey", "")).trim();
        String apiToken = String.valueOf(cfg.getOrDefault("apiToken", cfg.getOrDefault("token", "")))
                .trim();
        String username = String.valueOf(cfg.getOrDefault("username", cfg.getOrDefault("email", "")))
                .trim();

        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("Confluence 知识源未配置 Base URL");
        }

        String apiUrl = baseUrl + "/wiki/rest/api/content?type=page&limit=50&expand=body.storage,version";
        if (!spaceKey.isBlank()) {
            apiUrl += "&spaceKey=" + spaceKey;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");

        if (!username.isBlank() && !apiToken.isBlank()) {
            String auth =
                    Base64.getEncoder().encodeToString((username + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + auth);
        } else if (!apiToken.isBlank()) {
            builder.header("Authorization", "Bearer " + apiToken);
        }

        HttpResponse<String> response =
                HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Confluence API 调用失败 [HTTP " + response.statusCode() + "]: " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }

        List<RemoteKnowledgeDoc> docs = new ArrayList<>();
        for (JsonNode page : results) {
            String pageId = page.path("id").asText();
            String title = page.path("title").asText("Confluence Page");
            String storageVal = page.path("body").path("storage").path("value").asText("");
            String versionNumber = page.path("version").path("number").asText("1");

            String cleanBody = storageVal
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            String docUrl = baseUrl + "/wiki/spaces/" + spaceKey + "/pages/" + pageId;
            String content = "# " + title + "\n\n" + cleanBody;

            String hash = sha256(content + versionNumber);
            docs.add(new RemoteKnowledgeDoc(
                    docUrl,
                    title,
                    content,
                    hash,
                    System.currentTimeMillis(),
                    Map.of("pageId", pageId, "version", versionNumber)));
        }

        log.info("Confluence 空间扫描完成，成功获取页面 {} 篇", docs.size());
        return docs;
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
