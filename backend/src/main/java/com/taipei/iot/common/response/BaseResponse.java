package com.taipei.iot.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.taipei.iot.common.enums.ErrorCode;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString(exclude = "body") // body 可能含大量資料或敏感欄位，排除以避免 log 洩漏
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseResponse<T> {

	private String errorCode;

	private String errorMsg;

	private String errorDetail;

	/** 回應時間戳（秒精度），避免毫秒精度造成 timing side-channel（N-12）。 */
	private long timestamp;

	private T body;

	public static <T> BaseResponse<T> success(T body) {
		return BaseResponse.<T>builder()
			.errorCode(ErrorCode.SUCCESS.getCode())
			.errorMsg(ErrorCode.SUCCESS.getMessage())
			.timestamp(System.currentTimeMillis() / 1000)
			.body(body)
			.build();
	}

	public static <T> BaseResponse<T> fail(ErrorCode code) {
		return BaseResponse.<T>builder()
			.errorCode(code.getCode())
			.errorMsg(code.getMessage())
			.timestamp(System.currentTimeMillis() / 1000)
			.build();
	}

	public static <T> BaseResponse<T> fail(ErrorCode code, String detail) {
		// null 或空白 detail 不寫入，維持與 fail(ErrorCode) 的一致性（NON_NULL 序列化）
		String trimmed = (detail != null && !detail.isBlank()) ? detail : null;
		return BaseResponse.<T>builder()
			.errorCode(code.getCode())
			.errorMsg(code.getMessage())
			.errorDetail(trimmed)
			.timestamp(System.currentTimeMillis() / 1000)
			.build();
	}

	/** 回傳是否為成功回應（errorCode 為 {@link ErrorCode#SUCCESS}）。 */
	public boolean isSuccess() {
		return ErrorCode.SUCCESS.getCode().equals(this.errorCode);
	}

}
