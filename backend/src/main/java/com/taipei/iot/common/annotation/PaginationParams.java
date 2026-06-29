package com.taipei.iot.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 分頁參數註解 — 標註於 Controller 方法的 {@link com.taipei.iot.common.dto.PageQuery} 參數上，由
 * {@code PaginationArgumentResolver} 解析請求中的 {@code page} 與 {@code size} query string。
 *
 * <p>
 * 取代各 Controller 重複出現的： <pre>
 * &#64;RequestParam(defaultValue = "0") &#64;Min(0) int page,
 * &#64;RequestParam(defaultValue = "20") &#64;Min(1) &#64;Max(100) int size
 * </pre>
 *
 * <p>
 * 使用範例： <pre>
 * &#64;GetMapping
 * public BaseResponse&lt;PageResponse&lt;Foo&gt;&gt; list(&#64;PaginationParams PageQuery page) {
 *     return BaseResponse.success(service.list(page.getPage(), page.getSize()));
 * }
 * </pre>
 *
 * <p>
 * 邊界處理：
 * <ul>
 * <li>page &lt; 0 → 400 BAD_REQUEST</li>
 * <li>size &lt; 1 或 size &gt; {@link #maxSize()} → 400 BAD_REQUEST</li>
 * <li>非數字 / 空字串 → fallback 至 default 值</li>
 * </ul>
 *
 * <p>
 * 採「明示拒絕」而非「靜默 clamp」，以避免攻擊者刻意傳超大 size 想撈全表卻被 默默壓回 100 而不自知；同時與既有 {@code @Min/@Max}
 * 行為一致，向下相容現有測試。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PaginationParams {

	/**
	 * 預設頁碼（0-based）
	 */
	int defaultPage() default 0;

	/**
	 * 預設每頁筆數
	 */
	int defaultSize() default 20;

	/**
	 * 每頁筆數上限 — 防止使用者要求過大頁面造成 DB / 記憶體壓力
	 */
	int maxSize() default 100;

}
