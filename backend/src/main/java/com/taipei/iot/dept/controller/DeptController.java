package com.taipei.iot.dept.controller;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.dept.dto.CreateDeptRequest;
import com.taipei.iot.dept.dto.DeptDto;
import com.taipei.iot.dept.dto.DeptOptionVO;
import com.taipei.iot.dept.dto.UpdateDeptRequest;
import com.taipei.iot.dept.service.DeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/auth/dept")
@RequiredArgsConstructor
@Tag(name = "Dept", description = "部門管理：樹狀查詢、選單、查詢單筆與 CRUD")
public class DeptController {

	private final DeptService deptService;

	@GetMapping("/list")
	@PreAuthorize("hasAuthority('DEPT_LIST')")
	@Operation(summary = "取得部門樹", description = "回傳完整部門樹狀結構，供管理端與下拉選單使用")
	public BaseResponse<List<DeptDto>> getDeptTree() {
		return BaseResponse.success(deptService.getDeptTree());
	}

	@GetMapping("/options")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "取得部門選單", description = "回傳可用於一般下拉選單的部門清單")
	public BaseResponse<List<DeptOptionVO>> getDeptOptions() {
		return BaseResponse.success(deptService.getDeptOptions());
	}

	@GetMapping("/scope-options")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "取得可見部門選單", description = "依目前使用者資料範圍回傳可見的部門清單")
	public BaseResponse<List<DeptOptionVO>> getScopedDeptOptions() {
		return BaseResponse.success(deptService.getScopedDeptOptions());
	}

	@GetMapping("/{deptId}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "查詢單筆部門", description = "依部門 ID 回傳單筆部門資料")
	public BaseResponse<DeptDto> getDeptById(@PathVariable Long deptId) {
		return BaseResponse.success(deptService.getDeptById(deptId));
	}

	@PostMapping
	@PreAuthorize("hasAuthority('DEPT_CREATE')")
	@AuditEvent(AuditEventType.CREATE_DEPT)
	@Operation(summary = "新增部門", description = "建立新的部門資料")
	public BaseResponse<DeptDto> createDept(@Valid @RequestBody CreateDeptRequest request) {
		return BaseResponse.success(deptService.createDept(request));
	}

	@PutMapping
	@PreAuthorize("hasAuthority('DEPT_UPDATE')")
	@AuditEvent(AuditEventType.UPDATE_DEPT)
	@Operation(summary = "更新部門", description = "更新既有部門資料")
	public BaseResponse<DeptDto> updateDept(@Valid @RequestBody UpdateDeptRequest request) {
		return BaseResponse.success(deptService.updateDept(request));
	}

	@DeleteMapping("/{deptId}")
	@PreAuthorize("hasAuthority('DEPT_DELETE')")
	@AuditEvent(AuditEventType.DELETE_DEPT)
	@Operation(summary = "刪除部門", description = "依部門 ID 刪除部門資料")
	public BaseResponse<Void> deleteDept(@PathVariable Long deptId) {
		deptService.deleteDept(deptId);
		return BaseResponse.success(null);
	}

}
