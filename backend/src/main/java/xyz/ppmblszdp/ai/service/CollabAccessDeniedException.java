package xyz.ppmblszdp.ai.service;

/** 共享会话越权访问异常。映射为 HTTP 403。 */
public class CollabAccessDeniedException extends RuntimeException {

    public CollabAccessDeniedException(String message) {
        super(message);
    }
}
