package com.taipei.iot.common.exception;

import com.taipei.iot.common.enums.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	private final String detail;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.detail = null;
	}

	public BusinessException(ErrorCode errorCode, String detail) {
		super(detail != null ? errorCode.getMessage() + ": " + detail : errorCode.getMessage());
		this.errorCode = errorCode;
		this.detail = detail;
	}

	public BusinessException(ErrorCode errorCode, Throwable cause) {
		super(errorCode.getMessage(), cause);
		this.errorCode = errorCode;
		this.detail = null;
	}

	public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
		super(detail != null ? errorCode.getMessage() + ": " + detail : errorCode.getMessage(), cause);
		this.errorCode = errorCode;
		this.detail = detail;
	}

}
