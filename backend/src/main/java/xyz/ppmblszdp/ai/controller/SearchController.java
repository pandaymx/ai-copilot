package xyz.ppmblszdp.ai.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.dto.SearchResponse;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.repository.SearchRepository;

/**
 * 聊天历史全文检索接口。
 *
 * <p>
 * 暴露 {@code GET /api/chat/search?q=关键字&limit=20}：依据 {@code X-User-Id}
 * 仅检索当前用户归属会话下的消息，返回命中的 session_id、message_id、角色类型、
 * {@code ts_headline} 高亮片段与时间戳，按相关度降序。
 *
 * <p>
 * 身份解析严格复用 {@link UserIdentityFilter#resolveIdentity}（strict 模式缺
 * {@code X-User-Id} 抛 401）；{@code q} 为空时返回 400。底层检索能力由
 * {@link SearchRepository} 基于 PostgreSQL tsvector + pg_trgm 提供。
 */
@RestController
@RequestMapping("/api/chat")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchRepository searchRepository;
    private final AuthProperties authProperties;

    public SearchController(SearchRepository searchRepository, AuthProperties authProperties) {
        this.searchRepository = searchRepository;
        this.authProperties = authProperties;
    }

    @GetMapping("/search")
    public Mono<SearchResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        if (q == null || q.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "查询关键字 q 不能为空"));
        }
        final String query = q.trim();
        return Mono.fromCallable(() -> searchRepository.searchByUser(userId, query, limit))
                .subscribeOn(Schedulers.boundedElastic())
                .map(results -> new SearchResponse(query, results))
                .onErrorResume(ResponseStatusException.class, ex -> Mono.error(ex))
                .onErrorResume(Exception.class, ex -> {
                    log.warn("检索失败: {}", ex.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "检索失败，请稍后重试"));
                });
    }
}
