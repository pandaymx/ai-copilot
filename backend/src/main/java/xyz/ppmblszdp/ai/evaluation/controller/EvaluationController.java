package xyz.ppmblszdp.ai.evaluation.controller;

import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.ppmblszdp.ai.evaluation.dto.AbTestResultDto;
import xyz.ppmblszdp.ai.evaluation.dto.BenchmarkCase;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationRequests;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationResultDto;
import xyz.ppmblszdp.ai.evaluation.dto.EvaluationSummaryDto;
import xyz.ppmblszdp.ai.evaluation.service.EvaluationService;

/**
 * AI 评测与评估体系 REST 控制器。
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 获取基准测试用例列表（支持分类过滤） */
    @GetMapping("/benchmarks")
    public ResponseEntity<List<BenchmarkCase>> listBenchmarks(
            @RequestParam(value = "category", required = false) String category) {
        return ResponseEntity.ok(evaluationService.listBenchmarks(category));
    }

    /** 新建/导入基准测试用例 */
    @PostMapping("/benchmarks")
    public ResponseEntity<BenchmarkCase> addBenchmark(@RequestBody BenchmarkCase benchmark) {
        if (benchmark == null
                || benchmark.prompt() == null
                || benchmark.prompt().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(evaluationService.addBenchmark(benchmark));
    }

    /** 删除基准测试用例 */
    @DeleteMapping("/benchmarks/{id}")
    public ResponseEntity<Void> deleteBenchmark(@PathVariable("id") String id) {
        boolean deleted = evaluationService.deleteBenchmark(id);
        return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    /** 运行批量基准测试集评测 */
    @PostMapping("/run")
    public ResponseEntity<List<EvaluationResultDto>> runBatch(@RequestBody EvaluationRequests.RunRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(evaluationService.runBatchEvaluations(request));
    }

    /** 运行 A/B 盲测对比评测 */
    @PostMapping("/ab-test")
    public ResponseEntity<AbTestResultDto> runAbTest(@RequestBody EvaluationRequests.AbRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(evaluationService.runAbTest(request));
    }

    /** 单条问答即时裁判评分 */
    @PostMapping("/judge-single")
    public ResponseEntity<EvaluationResultDto> judgeSingle(@RequestBody EvaluationRequests.SingleJudgeRequest request) {
        if (request == null || request.prompt() == null || request.responseText() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(evaluationService.judgeSingle(request));
    }

    /** 获取评测大盘聚合统计数据 */
    @GetMapping("/summary")
    public ResponseEntity<EvaluationSummaryDto> getSummary() {
        return ResponseEntity.ok(evaluationService.getSummary());
    }

    /** 人工标注打分与覆盖修改 */
    @PutMapping("/results/{id}/annotate")
    public ResponseEntity<EvaluationResultDto> annotateResult(
            @PathVariable("id") String id, @RequestBody EvaluationRequests.HumanAnnotationRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest().build();
        }
        Optional<EvaluationResultDto> updated =
                evaluationService.annotateResult(id, req.humanScore(), req.humanAnnotation());
        return updated.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
