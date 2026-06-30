package com.taipei.iot.workflow.exception;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.response.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 簽核引擎專用例外處理器。
 *
 * <p>
 * 原邏輯位於 {@code common.exception.GlobalExceptionHandler}，為消除 common 對 workflow 模組的
 * 反向依賴（分層違規），遷移至本模組自帶 {@code @RestControllerAdvice}。
 */
@Slf4j
@RestControllerAdvice
public class WorkflowExceptionHandler {

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

}
