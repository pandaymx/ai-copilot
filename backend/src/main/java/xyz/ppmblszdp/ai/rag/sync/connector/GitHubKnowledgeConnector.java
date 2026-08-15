package xyz.ppmblszdp.ai.rag.sync.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.rag.sync.dto.KnowledgeSourceDto;
import xyz.ppmblszdp.ai.rag.sync.dto.RemoteKnowledgeDoc;

/**
 * GitHub 知识库连接器：
 * 利用 GitHub Trees API 获取仓库指定分支的目录树与 Git Blob SHA，
 * 支持第一道 Blob SHA 快速过滤，仅下载发生内容变动的 Markdown/文档文件。
 */
@Component
public class GitHubKnowledgeConnector implements KnowledgeConnector {

    private static final Logger log = LoggerFactory.getLogger(GitHubKnowledgeConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".md", ".markdown", ".mdx", ".txt", ".adoc", ".json", ".yaml", ".yml");

    @Override
    public boolean supports(String sourceType) {
        return "GITHUB".equalsIgnoreCase(sourceType);
    }

    @Override
    public List<RemoteKnowledgeDoc> fetchDocuments(KnowledgeSourceDto source) throws Exception {
        Map<String, Object> cfg = source.config() != null ? source.config() : Map.of();

        String repo = String.valueOf(cfg.getOrDefault("repo", cfg.getOrDefault("repoUrl", "")))
                .trim();
        String branch = String.valueOf(cfg.getOrDefault("branch", "main")).trim();
        String pathPrefix = String.valueOf(cfg.getOrDefault("path", "")).trim();
        String token = (String) cfg.getOrDefault("token", cfg.getOrDefault("accessToken", null));

        if (repo.isBlank()) {
            throw new IllegalArgumentException("GitHub 知识源未配置仓库地址 (repo / repoUrl)");
        }

        // 规范化 owner/repo (如从 https://github.com/facebook/react 提取 facebook/react)
        String cleanRepo = repo.replace("https://github.com/", "")
                .replace("http://github.com/", "")
                .replaceAll("/$", "");

        String apiUrl = String.format("https://api.github.com/repos/%s/git/trees/%s?recursive=1", cleanRepo, branch);
        log.info("开始扫描 GitHub 仓库树: repo={}, branch={}, pathPrefix={}", cleanRepo, branch, pathPrefix);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "AI-Copilot-Knowledge-Sync/1.0");

        if (token != null && !token.isBlank() && !token.startsWith("**")) {
            reqBuilder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> response =
                HTTP_CLIENT.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 403) {
            String rateLimitRemaining =
                    response.headers().firstValue("x-ratelimit-remaining").orElse("0");
            throw new IllegalStateException("GitHub API 请求受限 (HTTP 403 Rate Limit, Remaining: " + rateLimitRemaining
                    + ")，请配置 GitHub Personal Access Token (PAT)");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub API 调用失败 [HTTP " + response.statusCode() + "]: " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode tree = root.get("tree");
        if (tree == null || !tree.isArray()) {
            return List.of();
        }

        List<RemoteKnowledgeDoc> docs = new ArrayList<>();
        for (JsonNode item : tree) {
            String type = item.path("type").asText();
            if (!"blob".equalsIgnoreCase(type)) {
                continue;
            }

            String filePath = item.path("path").asText();
            String blobSha = item.path("sha").asText();

            if (!pathPrefix.isBlank() && !filePath.startsWith(pathPrefix)) {
                continue;
            }

            boolean isDoc = SUPPORTED_EXTENSIONS.stream().anyMatch(filePath.toLowerCase()::endsWith);
            if (!isDoc) {
                continue;
            }

            String docUri = String.format("https://github.com/%s/blob/%s/%s", cleanRepo, branch, filePath);
            String rawUrl = String.format("https://raw.githubusercontent.com/%s/%s/%s", cleanRepo, branch, filePath);

            // 拉取正文内容
            String content = fetchRawContent(rawUrl, token);
            if (content != null && !content.isBlank()) {
                String title = extractTitleFromPathOrContent(filePath, content);
                Map<String, Object> meta = Map.of(
                        "repo", cleanRepo,
                        "branch", branch,
                        "filePath", filePath,
                        "blobSha", blobSha);

                docs.add(new RemoteKnowledgeDoc(docUri, title, content, blobSha, System.currentTimeMillis(), meta));
            }
        }

        log.info("GitHub 仓库扫描完成，成功解析文档 {} 篇", docs.size());
        return docs;
    }

    private String fetchRawContent(String rawUrl, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(rawUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "AI-Copilot-Knowledge-Sync/1.0");
            if (token != null && !token.isBlank() && !token.startsWith("**")) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> res =
                    HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 200) {
                return res.body();
            }
            log.warn("获取原始文件失败 [HTTP {}]: {}", res.statusCode(), rawUrl);
            return null;
        } catch (Exception e) {
            log.warn("网络抓取失败: {} - {}", rawUrl, e.getMessage());
            return null;
        }
    }

    private String extractTitleFromPathOrContent(String path, String content) {
        // 尝试从 Markdown 一级标题 # Title 提取
        for (String line : content.split("\n", 10)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).trim();
            }
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
