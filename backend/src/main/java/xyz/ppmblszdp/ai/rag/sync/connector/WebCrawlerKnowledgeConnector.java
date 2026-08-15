package xyz.ppmblszdp.ai.rag.sync.connector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.ppmblszdp.ai.rag.sync.dto.KnowledgeSourceDto;
import xyz.ppmblszdp.ai.rag.sync.dto.RemoteKnowledgeDoc;

/**
 * Web 站点与文档爬取连接器：
 * 支持深度限制、同源过滤、URL 正则白名单与 HTML 正文提取。
 */
@Component
public class WebCrawlerKnowledgeConnector implements KnowledgeConnector {

    private static final Logger log = LoggerFactory.getLogger(WebCrawlerKnowledgeConnector.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern LINK_PATTERN = Pattern.compile("href=[\"']([^\"'#]+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public boolean supports(String sourceType) {
        return "WEBSITE".equalsIgnoreCase(sourceType)
                || "SITEMAP".equalsIgnoreCase(sourceType)
                || "DOCUMENTATION".equalsIgnoreCase(sourceType);
    }

    @Override
    public List<RemoteKnowledgeDoc> fetchDocuments(KnowledgeSourceDto source) throws Exception {
        Map<String, Object> cfg = source.config() != null ? source.config() : Map.of();

        String entryUrl = String.valueOf(cfg.getOrDefault("url", cfg.getOrDefault("siteUrl", "")))
                .trim();
        if (entryUrl.isBlank()) {
            throw new IllegalArgumentException("Web 知识源未配置站点入口 URL (url / siteUrl)");
        }

        int maxDepth = Math.min(parseInt(cfg.get("maxDepth"), 2), 4);
        int maxPages = Math.min(parseInt(cfg.get("maxPages"), 30), 100);
        String pathWhitelist =
                String.valueOf(cfg.getOrDefault("pathPattern", "")).trim();

        URI entryUri = URI.create(entryUrl);
        String baseHost = entryUri.getHost();

        Set<String> visited = new HashSet<>();
        Queue<UrlDepth> queue = new LinkedList<>();
        queue.offer(new UrlDepth(entryUrl, 1));
        visited.add(entryUrl);

        List<RemoteKnowledgeDoc> results = new ArrayList<>();

        while (!queue.isEmpty() && results.size() < maxPages) {
            UrlDepth current = queue.poll();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(current.url))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "AI-Copilot-Web-Crawler/1.0")
                        .build();

                HttpResponse<String> response =
                        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200 || response.body() == null) {
                    continue;
                }

                String contentType =
                        response.headers().firstValue("content-type").orElse("");
                if (!contentType.contains("text/html") && !contentType.contains("text/plain")) {
                    continue;
                }

                String rawHtml = response.body();
                String title = extractTitle(rawHtml, current.url);
                String cleanContent = cleanHtmlToMarkdown(rawHtml);

                if (!cleanContent.isBlank()) {
                    String hash = sha256(cleanContent);
                    results.add(new RemoteKnowledgeDoc(
                            current.url,
                            title,
                            cleanContent,
                            hash,
                            System.currentTimeMillis(),
                            Map.of("depth", current.depth, "host", baseHost)));
                }

                // 提取同源超链接并入队
                if (current.depth < maxDepth) {
                    List<String> links = extractLinks(rawHtml, current.url, baseHost, pathWhitelist);
                    for (String link : links) {
                        if (visited.add(link)) {
                            queue.offer(new UrlDepth(link, current.depth + 1));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("抓取页面失败 [{}]: {}", current.url, e.getMessage());
            }
        }

        log.info("Web 站点爬取完毕: url={}, 总抓取页面={}", entryUrl, results.size());
        return results;
    }

    private List<String> extractLinks(String html, String currentUrl, String baseHost, String pathWhitelist) {
        List<String> links = new ArrayList<>();
        Matcher matcher = LINK_PATTERN.matcher(html);
        URI baseUri = URI.create(currentUrl);

        while (matcher.find()) {
            String href = matcher.group(1).trim();
            if (href.startsWith("javascript:") || href.startsWith("mailto:") || href.startsWith("tel:")) {
                continue;
            }
            try {
                URI resolved = baseUri.resolve(href);
                String fullUrl = resolved.toString();
                // 仅限同源
                if (resolved.getHost() != null && resolved.getHost().equalsIgnoreCase(baseHost)) {
                    if (pathWhitelist.isBlank() || resolved.getPath().matches(pathWhitelist)) {
                        links.add(fullUrl);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return links;
    }

    private String extractTitle(String html, String defaultUrl) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("\\s+", " ").trim();
        }
        return defaultUrl;
    }

    private String cleanHtmlToMarkdown(String html) {
        // 移除 script, style, svg, noscript, nav, header, footer
        String clean = html.replaceAll("(?is)<(script|style|svg|noscript|nav|footer)[^>]*>.*?</\\1>", " ");
        // 提取正文内容并去除 HTML 标签
        clean = clean.replaceAll("<h([1-6])[^>]*>(.*?)</h\\1>", "\n\n# $2\n\n");
        clean = clean.replaceAll("<li[^>]*>(.*?)</li>", "\n- $1");
        clean = clean.replaceAll("<p[^>]*>(.*?)</p>", "\n\n$1\n\n");
        clean = clean.replaceAll("<br\\s*/?>", "\n");
        clean = clean.replaceAll("<[^>]+>", " ");
        clean = clean.replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&");
        return clean.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private int parseInt(Object val, int fallback) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private record UrlDepth(String url, int depth) {}
}
