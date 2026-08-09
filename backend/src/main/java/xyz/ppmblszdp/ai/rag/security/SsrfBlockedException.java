package xyz.ppmblszdp.ai.rag.security;

/**
 * SSRF 防护拦截异常。当目标 URL 命中安全规则（内网地址、元数据地址、非 HTTP 协议等）时抛出。
 * Controller 层捕获后应转为 HTTP 400 Bad Request。
 */
public class SsrfBlockedException extends RuntimeException {

    public SsrfBlockedException(String message) {
        super(message);
    }
}
