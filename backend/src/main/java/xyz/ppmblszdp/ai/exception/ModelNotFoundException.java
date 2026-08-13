package xyz.ppmblszdp.ai.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 模型在指定供应商下不存在或未启用。
 *
 * <p>错误信息中列出该 provider 下可用模型，便于排障。
 */
public class ModelNotFoundException extends AiException {

    public ModelNotFoundException(String providerId, String modelId, List<String> available) {
        String where = (providerId == null || providerId.isBlank()) ? "全局" : ("供应商 '%s' 下".formatted(providerId));
        super(
                "MODEL_NOT_FOUND",
                HttpStatus.BAD_REQUEST,
                "模型 '%s' 在%s不存在或未启用。该范围内可用模型: %s".formatted(modelId, where, available));
    }
}
