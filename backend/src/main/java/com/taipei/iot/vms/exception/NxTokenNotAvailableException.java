package com.taipei.iot.vms.exception;

public class NxTokenNotAvailableException extends RuntimeException {

	public NxTokenNotAvailableException(String message) {
		super(message);
	}

	public NxTokenNotAvailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
