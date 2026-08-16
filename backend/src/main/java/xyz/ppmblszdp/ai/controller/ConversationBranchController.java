package xyz.ppmblszdp.ai.controller;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import xyz.ppmblszdp.ai.dto.BranchDto.BranchCreateRequest;
import xyz.ppmblszdp.ai.dto.BranchDto.BranchDiff;
import xyz.ppmblszdp.ai.dto.BranchDto.BranchMergeRequest;
import xyz.ppmblszdp.ai.dto.BranchDto.BranchSummary;
import xyz.ppmblszdp.ai.dto.BranchDto.ConversationTree;
import xyz.ppmblszdp.ai.dto.BranchDto.MergeResult;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.ConversationTreeService;

/**
 * 对话分支与版本树 REST 端点。
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}/branches")
@CrossOrigin(origins = "*")
public class ConversationBranchController {

    private static final Logger log = LoggerFactory.getLogger(ConversationBranchController.class);

    private final ConversationTreeService treeService;
    private final AuthProperties authProperties;

    public ConversationBranchController(ConversationTreeService treeService, AuthProperties authProperties) {
        this.treeService = treeService;
        this.authProperties = authProperties;
    }

    @GetMapping
    public Mono<ResponseEntity<List<BranchSummary>>> listBranches(
            @PathVariable String sessionId, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    return ResponseEntity.ok(treeService.listBranches(sessionId, userId));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping
    public Mono<ResponseEntity<BranchSummary>> createBranch(
            @PathVariable String sessionId, @RequestBody BranchCreateRequest request, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    BranchSummary summary =
                            treeService.createBranch(sessionId, userId, request.forkFromMessageId(), request.label());
                    return ResponseEntity.status(HttpStatus.CREATED).body(summary);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/tree")
    public Mono<ResponseEntity<ConversationTree>> getTree(
            @PathVariable String sessionId,
            @RequestParam(required = false) String activeBranchId,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    return ResponseEntity.ok(treeService.assembleTree(sessionId, userId, activeBranchId));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/diff")
    public Mono<ResponseEntity<BranchDiff>> diffBranches(
            @PathVariable String sessionId,
            @RequestParam String branchA,
            @RequestParam String branchB,
            ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    return ResponseEntity.ok(treeService.diff(sessionId, userId, branchA, branchB));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/merge")
    public Mono<ResponseEntity<MergeResult>> mergeBranches(
            @PathVariable String sessionId, @RequestBody BranchMergeRequest request, ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
                    String userId = resolveUserId(exchange);
                    return ResponseEntity.ok(
                            treeService.merge(sessionId, userId, request.sourceBranchId(), request.targetBranchId()));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String resolveUserId(ServerWebExchange exchange) {
        return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
    }
}
