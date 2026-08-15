package xyz.ppmblszdp.ai.compare.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import xyz.ppmblszdp.ai.compare.dto.CompareChunkDto;
import xyz.ppmblszdp.ai.compare.dto.CompareRequest;
import xyz.ppmblszdp.ai.compare.dto.CompareResponseDto;
import xyz.ppmblszdp.ai.compare.service.ModelCompareService;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;

/**
 * 多模型并行调用与实时性能比对控制器。
 */
@RestController
@RequestMapping({"/api/chat/compare", "/api/compare"})
public class CompareController {

    private static final Logger log = LoggerFactory.getLogger(CompareController.class);

    private final ModelCompareService compareService;
    private final AuthProperties authProperties;

    public CompareController(ModelCompareService compareService, AuthProperties authProperties) {
        this.compareService = compareService;
        this.authProperties = authProperties;
    }

    /**
     * 非流式并行比对。
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<CompareResponseDto> compare(@RequestBody CompareRequest request, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return compareService.compare(request, userId);
    }

    /**
     * SSE 结构化多路复用流式并行比对。
     */
    @PostMapping(
            value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<CompareChunkDto>> stream(
            @RequestBody CompareRequest request, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return compareService
                .streamCompare(request, userId)
                .map(chunk -> ServerSentEvent.builder(chunk).build())
                .concatWithValues(ServerSentEvent.builder(CompareChunkDto.done(-1, "all", "all"))
                        .build())
                .onErrorResume(Exception.class, ex -> {
                    log.warn("多模型比对流异常: {}", ex.getMessage());
                    return Flux.just(
                            ServerSentEvent.builder(CompareChunkDto.error(-1, "global", "global", ex.getMessage()))
                                    .build());
                });
    }
}
