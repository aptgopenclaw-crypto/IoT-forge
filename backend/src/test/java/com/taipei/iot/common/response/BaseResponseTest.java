package com.taipei.iot.common.response;

import com.taipei.iot.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BaseResponseTest {

	// ─── success ─────────────────────────────────────────────────────────────

	@Test
	void success_setsSuccessCodeAndBody() {
		BaseResponse<String> resp = BaseResponse.success("hello");

		assertEquals(ErrorCode.SUCCESS.getCode(), resp.getErrorCode());
		assertEquals(ErrorCode.SUCCESS.getMessage(), resp.getErrorMsg());
		assertEquals("hello", resp.getBody());
		assertNull(resp.getErrorDetail(), "成功回應不應有 errorDetail");
		assertTrue(resp.getTimestamp() > 0, "timestamp 應為正數");
	}

	@Test
	void success_nullBody_bodyIsNull() {
		BaseResponse<Void> resp = BaseResponse.success(null);

		assertNull(resp.getBody());
		assertEquals(ErrorCode.SUCCESS.getCode(), resp.getErrorCode());
	}

	// ─── fail (no detail) ────────────────────────────────────────────────────

	@Test
	void fail_noDetail_setsErrorCodeAndMsg() {
		BaseResponse<?> resp = BaseResponse.fail(ErrorCode.USER_NOT_FOUND);

		assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), resp.getErrorCode());
		assertEquals(ErrorCode.USER_NOT_FOUND.getMessage(), resp.getErrorMsg());
		assertNull(resp.getBody(), "失敗回應不應有 body");
		assertNull(resp.getErrorDetail(), "未傳入 detail 時 errorDetail 應為 null");
		assertTrue(resp.getTimestamp() > 0);
	}

	// ─── fail (with detail) ───────────────────────────────────────────────────

	@Test
	void fail_withDetail_setsErrorDetail() {
		BaseResponse<?> resp = BaseResponse.fail(ErrorCode.USER_NOT_FOUND, "userId=99");

		assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), resp.getErrorCode());
		assertEquals("userId=99", resp.getErrorDetail());
		assertNull(resp.getBody());
	}

	@Test
	void fail_nullDetail_errorDetailIsNull() {
		BaseResponse<?> resp = BaseResponse.fail(ErrorCode.USER_NOT_FOUND, null);

		assertNull(resp.getErrorDetail(), "null detail 應等同無 detail");
	}

	@Test
	void fail_blankDetail_errorDetailIsNull() {
		BaseResponse<?> resp = BaseResponse.fail(ErrorCode.USER_NOT_FOUND, "   ");

		assertNull(resp.getErrorDetail(), "空白 detail 應被過濾，等同無 detail");
	}

	// ─── toString ────────────────────────────────────────────────────────────

	@Test
	void toString_excludesBody() {
		BaseResponse<String> resp = BaseResponse.success("SENSITIVE");
		String str = resp.toString();

		assertFalse(str.contains("SENSITIVE"), "toString() 不應包含 body 內容，以防止 log 敏感資料洩漏");
	}

	// ─── isSuccess ───────────────────────────────────────────────────────────

	@Test
	void isSuccess_successResponse_returnsTrue() {
		assertTrue(BaseResponse.success("data").isSuccess());
	}

	@Test
	void isSuccess_failResponse_returnsFalse() {
		assertFalse(BaseResponse.fail(ErrorCode.USER_NOT_FOUND).isSuccess());
	}

	// ─── timestamp ───────────────────────────────────────────────────────────

	@Test
	void timestamp_isSetToCurrentTimeInSeconds() {
		long before = System.currentTimeMillis() / 1000;
		BaseResponse<Void> resp = BaseResponse.success(null);
		long after = System.currentTimeMillis() / 1000;

		assertTrue(resp.getTimestamp() >= before && resp.getTimestamp() <= after, "timestamp 應介於建立前後的系統時間（秒精度）之間");
	}

	@Test
	void timestamp_secondPrecision_neverExceedsMaxReasonableEpochSeconds() {
		// N-12: 確保 timestamp 為秒精度而非毫秒（毫秒值在 2026 年已達 ~1.7×10^12）
		BaseResponse<Void> resp = BaseResponse.success(null);
		assertTrue(resp.getTimestamp() < 10_000_000_000L, "timestamp 應為秒精度 (< 10^10)，非毫秒精度");
	}

}
