package xyz.ppmblszdp.ai.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.ppmblszdp.ai.dto.TranslateRequestDto;
import xyz.ppmblszdp.ai.dto.TranslateResponseDto;
import xyz.ppmblszdp.ai.service.TranslationService;

/**
 * 多语言翻译 REST 控制器。
 * 暴露即时翻译与支持语种列表接口。
 */
@RestController
@RequestMapping("/api/translate")
@CrossOrigin(origins = "*")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    /**
     * 执行多语言即时翻译。
     */
    @PostMapping
    public ResponseEntity<TranslateResponseDto> translate(@Valid @RequestBody TranslateRequestDto request) {
        TranslateResponseDto response = translationService.translate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取系统支持的标准语种列表。
     */
    @GetMapping("/languages")
    public ResponseEntity<List<Map<String, String>>> getSupportedLanguages() {
        List<Map<String, String>> languages = translationService.getSupportedLanguages();
        return ResponseEntity.ok(languages);
    }
}
