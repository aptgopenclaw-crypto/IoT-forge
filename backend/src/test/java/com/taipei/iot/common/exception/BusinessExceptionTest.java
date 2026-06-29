package com.taipei.iot.common.exception;

import com.taipei.iot.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BusinessExceptionTest {

	@Test
	void constructor_errorCodeOnly_detailIsNull() {
		BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND);

		assertEquals(ErrorCode.USER_NOT_FOUND, ex.getErrorCode());
		assertNull(ex.getDetail());
		assertEquals(ErrorCode.USER_NOT_FOUND.getMessage(), ex.getMessage());
	}

	@Test
	void constructor_withDetail_detailIsSet() {
		BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND, "userId=42");

		assertEquals("userId=42", ex.getDetail());
		// getMessage() 此時包含 detail，方便 stack trace debug
		assertTrue(ex.getMessage().contains(ErrorCode.USER_NOT_FOUND.getMessage()), "getMessage() 應包含 errorCode 訊息");
		assertTrue(ex.getMessage().contains("userId=42"), "getMessage() 應包含 detail");
	}

	@Test
	void constructor_withCause_causeIsPreserved() {
		RuntimeException cause = new RuntimeException("root cause");
		BusinessException ex = new BusinessException(ErrorCode.UNKNOWN_ERROR, cause);

		assertSame(cause, ex.getCause(), "包裝原始例外時 cause 不應遺失");
		assertNull(ex.getDetail());
	}

	@Test
	void constructor_withDetailAndCause_bothPreserved() {
		RuntimeException cause = new RuntimeException("root cause");
		BusinessException ex = new BusinessException(ErrorCode.UNKNOWN_ERROR, "some detail", cause);

		assertEquals("some detail", ex.getDetail());
		assertSame(cause, ex.getCause());
		assertTrue(ex.getMessage().contains("some detail"), "getMessage() 應包含 detail");
	}

}
