package xyz.ppmblszdp.ai.exception;

import org.springframework.http.HttpStatus;

/**
 * 图像生成服务异常。
 */
public class ImageGenerationException extends AiException {

	public ImageGenerationException(String message) {
		super("IMAGE_GEN_FAILED", HttpStatus.BAD_GATEWAY, message);
	}

	public ImageGenerationException(String errorCode, String message) {
		super(errorCode, HttpStatus.BAD_REQUEST, message);
	}

	public ImageGenerationException(String errorCode, HttpStatus status, String message) {
		super(errorCode, status, message);
	}
}
