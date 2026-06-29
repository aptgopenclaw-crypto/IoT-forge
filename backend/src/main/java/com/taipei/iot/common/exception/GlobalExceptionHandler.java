package com.taipei.iot.common.exception;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.enums.SecurityEvent;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.util.SecurityLogger;
import com.taipei.iot.workflow.exception.WorkflowException;
import com.taipei.iot.workflow.exception.WorkflowInstanceNotFoundException;
import com.taipei.iot.workflow.exception.WorkflowInvalidTransitionException;
import com.taipei.iot.workflow.exception.WorkflowNotFoundException;
import com.taipei.iot.workflow.exception.WorkflowPermissionException;
import com.taipei.iot.workflow.exception.WorkflowStepAlreadyCompletedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(WorkflowInstanceNotFoundException.class)
	public ResponseEntity<BaseResponse<?>> handleWorkflowNotFound(WorkflowInstanceNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(BaseResponse.fail(ErrorCode.UNKNOWN_ERROR, ex.getMessage()));
	}

	@ExceptionHandler(WorkflowNotFoundException.class)
	public ResponseEntity<BaseResponse<?>> handleWorkflowDefNotFound(WorkflowNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(BaseResponse.fail(ErrorCode.UNKNOWN_ERROR, ex.getMessage()));
	}

	@ExceptionHandler(WorkflowPermissionException.class)
	public ResponseEntity<BaseResponse<?>> handleWorkflowPermission(WorkflowPermissionException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(BaseResponse.fail(ErrorCode.PERMISSION_DENIED, ex.getMessage()));
	}

	@ExceptionHandler({ WorkflowInvalidTransitionException.class, WorkflowStepAlreadyCompletedException.class })
	public ResponseEntity<BaseResponse<?>> handleWorkflowBadRequest(WorkflowException ex) {
		return ResponseEntity.badRequest().body(BaseResponse.fail(ErrorCode.VALIDATION_ERROR, ex.getMessage()));
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<BaseResponse<?>> handleBusiness(BusinessException ex) {
		ErrorCode code = ex.getErrorCode();
		String detail = ex.getDetail();
		// 5xx 代表服務端問題，升為 error；4xx 是 client 問題，用 warn 即可
		if (code.getHttpStatus() >= 500) {
			log.error("Business exception [{}] {}{}", code.getCode(), code.getMessage(),
					detail != null ? ": " + detail : "", ex);
		}
		else {
			log.warn("Business exception [{}] {}{}", code.getCode(), code.getMessage(),
					detail != null ? ": " + detail : "");
		}
		return ResponseEntity.status(code.getHttpStatus()).body(BaseResponse.fail(code, detail));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<BaseResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
		// 合併 FieldError（欄位層級）與 GlobalError（class-level 驗證，如 @ScriptAssert）
		Stream<String> fieldErrors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(e -> e.getField() + ": " + e.getDefaultMessage());
		Stream<String> globalErrors = ex.getBindingResult()
			.getGlobalErrors()
			.stream()
			.map(e -> e.getObjectName() + ": " + e.getDefaultMessage());
		String detail = Stream.concat(fieldErrors, globalErrors).collect(Collectors.joining("; "));
		return ResponseEntity.badRequest()
			.body(BaseResponse.fail(ErrorCode.VALIDATION_ERROR, detail.isBlank() ? null : detail));
	}

	/** @RequestParam 缺少，或 @Validated 作用在 method parameter 層級時觸發 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<BaseResponse<?>> handleMissingParam(MissingServletRequestParameterException ex) {
		log.warn("Missing request parameter: {}", ex.getMessage());
		return ResponseEntity.badRequest()
			.body(BaseResponse.fail(ErrorCode.VALIDATION_ERROR, ex.getParameterName() + ": parameter is required"));
	}

	/** Jakarta Bean Validation (@Validated on path/query parameters) */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<BaseResponse<?>> handleConstraintViolation(ConstraintViolationException ex) {
		String detail = ex.getConstraintViolations()
			.stream()
			.map(v -> v.getPropertyPath() + ": " + v.getMessage())
			.collect(Collectors.joining("; "));
		log.warn("Constraint violation: {}", detail);
		return ResponseEntity.badRequest()
			.body(BaseResponse.fail(ErrorCode.VALIDATION_ERROR, detail.isBlank() ? null : detail));
	}

	/** Spring MVC / filter 拋出的 ResponseStatusException，回傳其原始狀態碼 */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<BaseResponse<?>> handleResponseStatus(ResponseStatusException ex) {
		log.warn("ResponseStatusException [{}]: {}", ex.getStatusCode(), ex.getReason());
		String reason = ex.getReason();
		return ResponseEntity.status(ex.getStatusCode())
			.body(BaseResponse.fail(ErrorCode.UNKNOWN_ERROR, reason != null && !reason.isBlank() ? reason : null));
	}

	/** JSON 格式錯誤（缺少 body、語法錯誤、型別不匹配）— 屬 client 4xx，不回 5xx */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<BaseResponse<?>> handleUnreadable(HttpMessageNotReadableException ex) {
		log.warn("Unreadable request body: {}", ex.getMessage());
		return ResponseEntity.badRequest().body(BaseResponse.fail(ErrorCode.VALIDATION_ERROR, "請求格式錯誤"));
	}

	/**
	 * AccessDeniedException / AuthorizationDeniedException（Spring Security 6.3+）
	 * — @PreAuthorize 失敗時由 DispatcherServlet 路由到此處（而非 filter chain 的
	 * accessDeniedHandler）。
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<BaseResponse<?>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		SecurityLogger.warn(SecurityEvent.ACCESS_DENIED, request.getRemoteAddr(), "path=" + request.getRequestURI(),
				"user=" + (request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous"));
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(BaseResponse.fail(ErrorCode.PERMISSION_DENIED));
	}

	/**
	 * N-6（2026-05-27）：上傳檔案超過 {@code spring.servlet.multipart.max-file-size} 時 Spring 拋出
	 * {@link MaxUploadSizeExceededException}。改回 HTTP 413 (Payload Too Large) +
	 * {@link ErrorCode#FILE_SIZE_EXCEEDED}，避免落入 catch-all 而回 500 + stacktrace（資訊洩漏 +
	 * 體驗差）。
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<BaseResponse<?>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
		log.warn("Upload exceeds size limit: maxUploadSize={} bytes ({})", ex.getMaxUploadSize(), ex.getMessage());
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
			.body(BaseResponse.fail(ErrorCode.FILE_SIZE_EXCEEDED, "上傳檔案大小超過限制"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<BaseResponse<?>> handleException(Exception ex, HttpServletRequest request,
			HttpServletResponse response) {
		// 檢測疑似 SQL Injection 輸入
		String message = ex.getMessage();
		if (message != null && isSuspiciousInput(message)) {
			SecurityLogger.warn(SecurityEvent.SUSPICIOUS_INPUT, request.getRemoteAddr(),
					"path=" + request.getRequestURI(), "pattern=possible_sql_injection");
		}
		log.error("Unhandled exception", ex);
		// [F-fix] 若前段（filter / security handler）已呼叫過 getWriter()，
		// 但 response 尚未真正 commit（flush），先呼叫 reset() 清除 writer 佔用狀態，
		// 否則 Jackson 無法取得 getOutputStream() 而拋出第二層 500。
		if (!response.isCommitted()) {
			response.reset();
		}
		else {
			// 回應已送出，無法再寫入；記錄並靜默回傳（避免第二層 exception）
			log.warn("Response already committed — cannot write error body for: {}", request.getRequestURI());
			return null;
		}
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(BaseResponse.fail(ErrorCode.UNKNOWN_ERROR));
	}

	private static boolean isSuspiciousInput(String message) {
		String lower = message.toLowerCase();
		return lower.contains("sql") && (lower.contains("syntax") || lower.contains("injection"))
				|| lower.contains("' or '1'='1") || lower.contains("union select") || lower.contains("drop table")
				|| lower.contains("'; --") || lower.contains("script>");
	}

}
