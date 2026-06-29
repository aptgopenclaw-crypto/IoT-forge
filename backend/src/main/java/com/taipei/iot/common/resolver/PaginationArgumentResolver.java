package com.taipei.iot.common.resolver;

import com.taipei.iot.common.annotation.PaginationParams;
import com.taipei.iot.common.dto.PageQuery;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/**
 * 解析 Controller 方法上標註 {@link PaginationParams} 的 {@link PageQuery} 參數。
 *
 * <p>
 * 從 query string 讀取 {@code page} / {@code size}，套用 default、邊界檢查後注入 Controller。 詳細語意請見
 * {@link PaginationParams}。
 */
public class PaginationArgumentResolver implements HandlerMethodArgumentResolver {

	static final String PAGE_PARAM = "page";
	static final String SIZE_PARAM = "size";

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(PaginationParams.class)
				&& PageQuery.class.isAssignableFrom(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(@NonNull MethodParameter parameter, ModelAndViewContainer mavContainer,
			@NonNull NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		PaginationParams ann = parameter.getParameterAnnotation(PaginationParams.class);
		if (ann == null) {
			// supportsParameter 已過濾，理論上不會發生；保險起見走預設值
			return new PageQuery(0, 20);
		}

		int page = parseIntOrDefault(webRequest.getParameter(PAGE_PARAM), ann.defaultPage(), PAGE_PARAM);
		int size = parseIntOrDefault(webRequest.getParameter(SIZE_PARAM), ann.defaultSize(), SIZE_PARAM);

		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page 必須 >= 0，實際: " + page);
		}
		if (size < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size 必須 >= 1，實際: " + size);
		}
		if (size > ann.maxSize()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size 不得超過 " + ann.maxSize() + "，實際: " + size);
		}

		return new PageQuery(page, size);
	}

	private static int parseIntOrDefault(String raw, int fallback, String fieldName) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(raw.trim());
		}
		catch (NumberFormatException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " 必須為整數，實際: " + raw);
		}
	}

}
