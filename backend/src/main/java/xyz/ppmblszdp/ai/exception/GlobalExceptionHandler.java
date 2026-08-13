package xyz.ppmblszdp.ai.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：将异常统一转换为结构化错误响应 {@code { code, message, ... }}。
 *
 * <p>
 * 区分 4xx（配置 / 参数错误）与 5xx（上游厂商故障）。日志脱敏：不输出 apiKey 与完整请求体，
 * 仅输出错误码、消息与必要的可用列表。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AiException.class)
    public ResponseEntity<Map<String, Object>> handleAi(AiException ex) {
        log.warn("业务异常 [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(body(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegal(IllegalArgumentException ex) {
        log.warn("参数错误: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body("INVALID_ARGUMENT", ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            org.springframework.web.server.ResponseStatusException ex) {
        log.warn("HTTP 状态异常 [{}]: {}", ex.getStatusCode(), ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(body(ex.getStatusCode().toString(), ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        log.error("未预期异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body("INTERNAL_ERROR", "服务内部错误，请稍后重试"));
    }

    private Map<String, Object> body(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        return m;
    }
}
