package com.taipei.iot.common.exception;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.response.BaseResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

	@InjectMocks
	private GlobalExceptionHandler handler;

	// ─── BusinessException ───────────────────────────────────────────────────

	@Test
	void handleBusiness_returnsCorrectStatusAndErrorCode() {
		BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND);

		ResponseEntity<BaseResponse<?>> response = handler.handleBusiness(ex);

		assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(ErrorCode.USER_NOT_FOUND.getCode(), response.getBody().getErrorCode());
		assertEquals(ErrorCode.USER_NOT_FOUND.getMessage(), response.getBody().getErrorMsg());
	}

	@Test
	void handleBusiness_withDetail_populatesErrorDetail() {
		BusinessException ex = new BusinessException(ErrorCode.USER_NOT_FOUND, "userId=42");

		ResponseEntity<BaseResponse<?>> response = handler.handleBusiness(ex);

		assertEquals("userId=42", response.getBody().getErrorDetail());
	}

	@Test
	void handleBusiness_withoutDetail_errorDetailIsNull() {
		BusinessException ex = new BusinessException(ErrorCode.ROLE_NOT_FOUND);

		ResponseEntity<BaseResponse<?>> response = handler.handleBusiness(ex);

		assertNull(response.getBody().getErrorDetail());
	}

	// ─── MethodArgumentNotValidException ─────────────────────────────────────

	// ── shared helper ─────────────────────────────────────────────────────────

	/** 建立一個合法的 MethodParameter，避免傳入 null 造成未來 Spring 版本的 NPE (R3-05) */
	private static MethodParameter dummyMethodParameter() throws NoSuchMethodException {
		Method m = Object.class.getDeclaredMethod("toString");
		return new MethodParameter(m, -1);
	}

	@Test
	void handleValidation_returns400WithValidationErrorCode() throws Exception {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
		bindingResult.addError(new FieldError("target", "username", "must not be blank"));
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

		ResponseEntity<BaseResponse<?>> response = handler.handleValidation(ex);

		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), response.getBody().getErrorCode(),
				"驗證失敗應回傳 VALIDATION_ERROR 而非 UNKNOWN_ERROR");
	}

	@Test
	void handleValidation_errorDetailContainsFieldName() throws Exception {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
		bindingResult.addError(new FieldError("target", "email", "must be a valid email"));
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

		ResponseEntity<BaseResponse<?>> response = handler.handleValidation(ex);

		assertTrue(response.getBody().getErrorDetail().contains("email"), "detail 應包含欄位名稱 email");
	}

	@Test
	void handleValidation_multipleErrors_joinsWithSemicolon() throws Exception {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
		bindingResult.addError(new FieldError("target", "username", "must not be blank"));
		bindingResult.addError(new FieldError("target", "email", "invalid email"));
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

		ResponseEntity<BaseResponse<?>> response = handler.handleValidation(ex);

		String detail = response.getBody().getErrorDetail();
		assertTrue(detail.contains(";"), "多個欄位錯誤應以分號分隔");
	}

	@Test
	void handleValidation_classLevelObjectError_includedInDetail() throws Exception {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "myTarget");
		bindingResult.addError(new ObjectError("myTarget", "passwords do not match"));
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

		ResponseEntity<BaseResponse<?>> response = handler.handleValidation(ex);

		String detail = response.getBody().getErrorDetail();
		assertNotNull(detail, "class-level 驗證錯誤應在 detail 中回傳");
		assertTrue(detail.contains("passwords do not match"), "detail 應包含 class-level 驗證訊息");
	}
	// ─── ResponseStatusException (R5-01) ──────────────────────────────────────

	@Test
	void handleResponseStatus_preservesOriginalStatus() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limit exceeded");

		ResponseEntity<BaseResponse<?>> response = handler.handleResponseStatus(ex);

		assertEquals(429, response.getStatusCode().value(), "ResponseStatusException 的 HTTP 狀態碼應被保留，不可變成 500");
		assertNotNull(response.getBody());
	}

	@Test
	void handleResponseStatus_withReason_reasonAppearsInDetail() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "maintenance");

		ResponseEntity<BaseResponse<?>> response = handler.handleResponseStatus(ex);

		assertEquals(503, response.getStatusCode().value());
		assertEquals("maintenance", response.getBody().getErrorDetail());
	}
	// ─── HttpMessageNotReadableException (R3-01) ─────────────────────────────

	@Test
	void handleUnreadable_returns400WithValidationErrorCode() {
		HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Bad JSON",
				mock(HttpInputMessage.class));

		ResponseEntity<BaseResponse<?>> response = handler.handleUnreadable(ex);

		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
		assertNotNull(response.getBody());
		assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), response.getBody().getErrorCode());
		assertEquals("請求格式錯誤", response.getBody().getErrorDetail());
	}

	// ─── MissingServletRequestParameterException (R3-01) ─────────────────────

	@Test
	void handleMissingParam_returns400WithParameterName() {
		MissingServletRequestParameterException ex = new MissingServletRequestParameterException("page", "Integer");

		ResponseEntity<BaseResponse<?>> response = handler.handleMissingParam(ex);

		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
		assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), response.getBody().getErrorCode());
		assertTrue(response.getBody().getErrorDetail().contains("page"), "detail 應包含缺少的參數名稱");
	}

	// ─── ConstraintViolationException (R3-01) ────────────────────────────────

	@Test
	@SuppressWarnings("unchecked")
	void handleConstraintViolation_returns400WithViolationDetail() {
		ConstraintViolation<?> violation = mock(ConstraintViolation.class);
		Path propertyPath = mock(Path.class);
		when(propertyPath.toString()).thenReturn("pageSize");
		when(violation.getPropertyPath()).thenReturn(propertyPath);
		when(violation.getMessage()).thenReturn("must be greater than 0");

		ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

		ResponseEntity<BaseResponse<?>> response = handler.handleConstraintViolation(ex);

		assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value());
		assertEquals(ErrorCode.VALIDATION_ERROR.getCode(), response.getBody().getErrorCode());
		String detail = response.getBody().getErrorDetail();
		assertTrue(detail.contains("pageSize"), "detail 應包含 property path");
		assertTrue(detail.contains("must be greater than 0"), "detail 應包含違規訊息");
	}

	// ─── AccessDeniedException ────────────────────────────────────────────────
	// Removed: AccessDeniedException is now handled exclusively by
	// SecurityConfig.accessDeniedHandler
	// to avoid getWriter()/getOutputStream() conflict.

	// ─── Generic Exception ────────────────────────────────────────────────────

	@Test
	void handleException_returns500WithUnknownErrorCode() {
		Exception ex = new RuntimeException("unexpected");
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse servletResponse = mock(HttpServletResponse.class);

		ResponseEntity<BaseResponse<?>> response = handler.handleException(ex, request, servletResponse);

		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
		assertEquals(ErrorCode.UNKNOWN_ERROR.getCode(), response.getBody().getErrorCode());
	}

	@Test
	void handleException_doesNotExposeInternalDetail() {
		Exception ex = new RuntimeException("internal detail that should not leak");
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse servletResponse = mock(HttpServletResponse.class);

		ResponseEntity<BaseResponse<?>> response = handler.handleException(ex, request, servletResponse);

		assertNull(response.getBody().getErrorDetail(), "500 回應不應將 exception message 暴露給呼叫端");
	}

	// ─── N-6: MaxUploadSizeExceededException ─────────────────────────────────

	@Test
	void handleMaxUploadSize_returns413WithFileSizeExceededCode() {
		MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10L * 1024 * 1024);

		ResponseEntity<BaseResponse<?>> response = handler.handleMaxUploadSize(ex);

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE.value(), response.getStatusCode().value(),
				"應回傳 413 Payload Too Large（而非 catch-all 的 500）");
		assertEquals(ErrorCode.FILE_SIZE_EXCEEDED.getCode(), response.getBody().getErrorCode());
	}

	@Test
	void handleMaxUploadSize_returnsFriendlyDetailWithoutStacktrace() {
		MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10L * 1024 * 1024,
				new RuntimeException("internal multipart parser cause should not leak"));

		ResponseEntity<BaseResponse<?>> response = handler.handleMaxUploadSize(ex);

		String detail = response.getBody().getErrorDetail();
		assertEquals("上傳檔案大小超過限制", detail, "detail 應為固定友善訊息，不可洩漏內部 cause");
		assertFalse(detail == null || detail.toLowerCase().contains("multipart"), "detail 不應包含 cause / stacktrace 內容");
	}

	@Test
	void handleMaxUploadSize_doesNotFallIntoGenericHandler() {
		// 確保 MaxUploadSizeExceededException 不會掉到 handleException → 500
		MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(1024L);

		ResponseEntity<BaseResponse<?>> response = handler.handleMaxUploadSize(ex);

		assertNotEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatusCode().value());
		assertNotEquals(ErrorCode.UNKNOWN_ERROR.getCode(), response.getBody().getErrorCode());
	}

}
