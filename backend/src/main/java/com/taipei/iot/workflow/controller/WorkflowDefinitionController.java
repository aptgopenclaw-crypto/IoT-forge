package com.taipei.iot.workflow.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.workflow.dto.WorkflowDefinitionRequest;
import com.taipei.iot.workflow.entity.WorkflowDefinitionEntity;
import com.taipei.iot.workflow.service.WorkflowDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.taipei.iot.common.audit.annotation.AuditEvent;
import com.taipei.iot.common.audit.enums.AuditEventType;

import java.util.List;

@RestController
@RequestMapping("/v1/auth/workflow/definitions")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "features.workflow.enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "WorkflowDefinition", description = "工作流程定義管理")
public class WorkflowDefinitionController {

	private final WorkflowDefinitionService service;

	@GetMapping
	@PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_MANAGE')")
	@Operation(summary = "取得所有工作流程定義")
	public BaseResponse<List<WorkflowDefinitionEntity>> list() {
		return BaseResponse.success(service.listAll());
	}

	@PostMapping
	@PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_MANAGE')")
	@Operation(summary = "新增工作流程定義")
	@AuditEvent(AuditEventType.WORKFLOW_CREATE)
	public BaseResponse<WorkflowDefinitionEntity> create(@Valid @RequestBody WorkflowDefinitionRequest req) {
		return BaseResponse.success(service.create(req));
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_MANAGE')")
	@Operation(summary = "更新工作流程定義")
	@AuditEvent(AuditEventType.WORKFLOW_UPDATE)
	public BaseResponse<WorkflowDefinitionEntity> update(@PathVariable Long id,
			@Valid @RequestBody WorkflowDefinitionRequest req) {
		return BaseResponse.success(service.update(id, req));
	}

	@PatchMapping("/{id}/enabled")
	@PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_MANAGE')")
	@Operation(summary = "切換啟用狀態")
	@AuditEvent(AuditEventType.WORKFLOW_SWITCH_ENABLED)
	public BaseResponse<Void> toggleEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
		service.toggleEnabled(id, enabled);
		return BaseResponse.success(null);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('WORKFLOW_DEFINITION_MANAGE')")
	@Operation(summary = "刪除工作流程定義")
	@AuditEvent(AuditEventType.WORKFLOW_DELETE)
	public BaseResponse<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return BaseResponse.success(null);
	}

}
