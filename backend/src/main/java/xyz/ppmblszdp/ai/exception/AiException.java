package xyz.ppmblszdp.ai.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务异常基类，携带结构化 errorCode 与对应 HTTP 状态。
 */
public abstract class AiException extends RuntimeException {

	private final String errorCode;
	private final HttpStatus httpStatus;

	protected AiException(String errorCode, HttpStatus httpStatus, String message) {
		super(message);
		this.errorCode = errorCode;
		this.httpStatus = httpStatus;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
}
