package com.taipei.iot.common.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

	// ─── 不變式：code 唯一性 ──────────────────────────────────────────────────

	@Test
	void allErrorCodes_areUnique() {
		long distinctCount = Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode).distinct().count();
		assertEquals(ErrorCode.values().length, distinctCount,
				"ErrorCode.code 出現重複值，請檢查 enum 定義。" + "重複的 code 會造成 client 端無法區分不同錯誤類型。");
	}

	// ─── 不變式：httpStatus 有效範圍 ──────────────────────────────────────────

	@Test
	void allErrorCodes_httpStatusIsValidHttpRange() {
		Arrays.stream(ErrorCode.values())
			.forEach(code -> assertTrue(code.getHttpStatus() >= 200 && code.getHttpStatus() < 600,
					code.name() + " 的 httpStatus=" + code.getHttpStatus() + " 不在有效 HTTP 狀態碼範圍內（200-599）"));
	}

	// ─── 不變式：SUCCESS 永遠是 200 ──────────────────────────────────────────

	@Test
	void success_hasHttp200AndCode00000() {
		assertEquals(200, ErrorCode.SUCCESS.getHttpStatus());
		assertEquals("00000", ErrorCode.SUCCESS.getCode());
	}

	// ─── 不變式：訊息非空 ─────────────────────────────────────────────────────

	@Test
	void allErrorCodes_haveNonBlankMessage() {
		Arrays.stream(ErrorCode.values())
			.forEach(code -> assertNotNull(code.getMessage(), code.name() + " 的 message 不應為 null"));
		Arrays.stream(ErrorCode.values())
			.forEach(code -> assertFalse(code.getMessage().isBlank(), code.name() + " 的 message 不應為空字串"));
	}

}
