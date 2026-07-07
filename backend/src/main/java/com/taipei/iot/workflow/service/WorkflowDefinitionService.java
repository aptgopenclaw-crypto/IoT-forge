package com.taipei.iot.workflow.service;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.workflow.dto.WorkflowDefinitionRequest;
import com.taipei.iot.workflow.entity.WorkflowDefinitionEntity;
import com.taipei.iot.workflow.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowDefinitionService {

	private final WorkflowDefinitionRepository repository;

	public List<WorkflowDefinitionEntity> listAll() {
		return repository.findAllForManagement();
	}

	@Transactional
	public WorkflowDefinitionEntity create(WorkflowDefinitionRequest req) {
		WorkflowDefinitionEntity entity = WorkflowDefinitionEntity.builder()
			.tenantId(TenantContext.getCurrentTenantId())
			.code(req.code())
			.name(req.name())
			.stepsJson(req.stepsJson())
			.enabled(req.enabled())
			.createdAt(LocalDateTime.now())
			.updatedAt(LocalDateTime.now())
			.build();
		return repository.save(entity);
	}

	@Transactional
	public WorkflowDefinitionEntity update(Long id, WorkflowDefinitionRequest req) {
		WorkflowDefinitionEntity entity = findById(id);
		entity.setCode(req.code());
		entity.setName(req.name());
		entity.setStepsJson(req.stepsJson());
		entity.setEnabled(req.enabled());
		entity.setUpdatedAt(LocalDateTime.now());
		return repository.save(entity);
	}

	@Transactional
	public void toggleEnabled(Long id, boolean enabled) {
		WorkflowDefinitionEntity entity = findById(id);
		entity.setEnabled(enabled);
		entity.setUpdatedAt(LocalDateTime.now());
		repository.save(entity);
	}

	@Transactional
	public void delete(Long id) {
		findById(id);
		repository.deleteById(id);
	}

	private WorkflowDefinitionEntity findById(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "工作流程定義不存在：" + id));
	}

}
