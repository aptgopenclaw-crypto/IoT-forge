package com.taipei.iot.dept.service;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dept.dto.CreateDeptRequest;
import com.taipei.iot.dept.dto.DeptDto;
import com.taipei.iot.dept.dto.DeptOptionVO;
import com.taipei.iot.dept.dto.UpdateDeptRequest;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeptService {

	private final DeptInfoRepository deptInfoRepository;

	private final UserTenantMappingRepository userTenantMappingRepository;

	private final UserRepository userRepository;

	private final DataScopeHelper dataScopeHelper;

	@Transactional(readOnly = true)
	public List<DeptDto> getDeptTree() {
		List<DeptInfoEntity> all = deptInfoRepository.findAllByStatusOrderByDeptSortAsc((short) 1);
		return buildTree(all);
	}

	@Transactional(readOnly = true)
	public List<DeptOptionVO> getDeptOptions() {
		List<DeptInfoEntity> all = deptInfoRepository.findAllByStatusOrderByDeptSortAsc((short) 1);
		return buildOptionTree(all);
	}

	/**
	 * 依據當前使用者的 DataScope 回傳可選擇的部門選項。 ALL → 全部部門；THIS_LEVEL / THIS_LEVEL_AND_BELOW → 限縮範圍。
	 */
	@Transactional(readOnly = true)
	public List<DeptOptionVO> getScopedDeptOptions() {
		List<DeptInfoEntity> all = deptInfoRepository.findAllByStatusOrderByDeptSortAsc((short) 1);
		List<Long> visibleDeptIds = dataScopeHelper.getVisibleDeptIds();

		if (visibleDeptIds.isEmpty()) {
			// ALL scope → 不限制
			return buildOptionTree(all);
		}

		// 過濾出可見的部門
		List<DeptInfoEntity> filtered = all.stream()
			.filter(d -> visibleDeptIds.contains(d.getDeptId()))
			.collect(Collectors.toList());
		return buildScopedOptionTree(filtered);
	}

	@Transactional(readOnly = true)
	public DeptDto getDeptById(Long deptId) {
		DeptInfoEntity entity = deptInfoRepository.findByDeptId(deptId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
		return toDto(entity);
	}

	@Transactional
	public DeptDto createDept(CreateDeptRequest request) {
		String tenantId = TenantContext.getCurrentTenantId();

		// Check duplicate name under same parent
		if (deptInfoRepository.existsByTenantIdAndDeptNameAndPid(tenantId, request.getDeptName(), request.getPid())) {
			throw new BusinessException(ErrorCode.DEPT_ALREADY_EXISTS);
		}

		// Validate parent exists if pid is provided
		String parentPath = "";
		if (request.getPid() != null) {
			DeptInfoEntity parent = deptInfoRepository.findByDeptId(request.getPid())
				.orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));
			parentPath = parent.getHierarchyPath();
		}

		String currentUser = SecurityContextUtils.getCurrentUsername();

		DeptInfoEntity entity = DeptInfoEntity.builder()
			.tenantId(tenantId)
			.pid(request.getPid())
			.deptName(request.getDeptName())
			.deptSort(request.getDeptSort() != null ? request.getDeptSort() : 0)
			.status((short) 1)
			.createBy(currentUser)
			.build();

		DeptInfoEntity saved;
		try {
			saved = deptInfoRepository.save(entity);
		}
		catch (DataIntegrityViolationException e) {
			// TOCTOU race: another request created the same dept between exists-check and
			// save
			throw new BusinessException(ErrorCode.DEPT_ALREADY_EXISTS);
		}

		// Build hierarchy_path after save (need the generated dept_id)
		if (request.getPid() == null) {
			saved.setHierarchyPath("/" + saved.getDeptId() + "/");
		}
		else {
			saved.setHierarchyPath(parentPath + saved.getDeptId() + "/");
		}
		saved = deptInfoRepository.save(saved);

		return toDto(saved);
	}

	@Transactional
	public DeptDto updateDept(UpdateDeptRequest request) {
		DeptInfoEntity entity = deptInfoRepository.findByDeptId(request.getDeptId())
			.orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));

		// Check duplicate name if name is changing
		if (request.getDeptName() != null && !request.getDeptName().equals(entity.getDeptName())) {
			if (deptInfoRepository.existsByTenantIdAndDeptNameAndPid(entity.getTenantId(), request.getDeptName(),
					entity.getPid())) {
				throw new BusinessException(ErrorCode.DEPT_ALREADY_EXISTS);
			}
			entity.setDeptName(request.getDeptName());
		}

		if (request.getDeptSort() != null) {
			entity.setDeptSort(request.getDeptSort());
		}
		if (request.getStatus() != null) {
			entity.setStatus(request.getStatus());
		}

		entity.setUpdateBy(SecurityContextUtils.getCurrentUsername());

		DeptInfoEntity saved = deptInfoRepository.save(entity);
		return toDto(saved);
	}

	@Transactional
	public void deleteDept(Long deptId) {
		DeptInfoEntity entity = deptInfoRepository.findByDeptId(deptId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DEPT_NOT_FOUND));

		// Check: has children?
		if (deptInfoRepository.existsByPid(deptId)) {
			throw new BusinessException(ErrorCode.DEPT_HAS_CHILDREN);
		}

		// Check: has active (non-deleted) users in user_tenant_mapping?
		List<String> blockingUserIds = userTenantMappingRepository
			.findByTenantIdAndDeptIdAndEnabledTrue(entity.getTenantId(), deptId)
			.stream()
			.map(m -> m.getUserId())
			.collect(Collectors.toList());
		if (!blockingUserIds.isEmpty()) {
			List<String> activeNames = userRepository.findAllById(blockingUserIds)
				.stream()
				.filter(u -> !u.getDeleted())
				.map(UserEntity::getDisplayName)
				.collect(Collectors.toList());
			if (!activeNames.isEmpty()) {
				throw new BusinessException(ErrorCode.DEPT_HAS_USERS, String.join(", ", activeNames));
			}
		}

		// Clear dept_id references in user_tenant_mapping to avoid FK violation
		userTenantMappingRepository.clearDeptIdByTenantIdAndDeptId(entity.getTenantId(), deptId);

		deptInfoRepository.delete(entity);
	}

	// ---- Private helpers ----

	private List<DeptDto> buildTree(List<DeptInfoEntity> all) {
		Map<Long, List<DeptInfoEntity>> childrenMap = all.stream()
			.filter(e -> e.getPid() != null)
			.collect(Collectors.groupingBy(DeptInfoEntity::getPid));

		List<DeptDto> roots = new ArrayList<>();
		for (DeptInfoEntity entity : all) {
			if (entity.getPid() == null) {
				DeptDto dto = toDtoWithChildren(entity, childrenMap);
				roots.add(dto);
			}
		}
		return roots;
	}

	private DeptDto toDtoWithChildren(DeptInfoEntity entity, Map<Long, List<DeptInfoEntity>> childrenMap) {
		DeptDto dto = toDto(entity);
		List<DeptInfoEntity> children = childrenMap.get(entity.getDeptId());
		if (children != null) {
			dto.setChildren(
					children.stream().map(child -> toDtoWithChildren(child, childrenMap)).collect(Collectors.toList()));
		}
		return dto;
	}

	private List<DeptOptionVO> buildOptionTree(List<DeptInfoEntity> all) {
		Map<Long, List<DeptInfoEntity>> childrenMap = all.stream()
			.filter(e -> e.getPid() != null)
			.collect(Collectors.groupingBy(DeptInfoEntity::getPid));

		List<DeptOptionVO> roots = new ArrayList<>();
		for (DeptInfoEntity entity : all) {
			if (entity.getPid() == null) {
				DeptOptionVO vo = toOptionWithChildren(entity, childrenMap);
				roots.add(vo);
			}
		}
		return roots;
	}

	/**
	 * 建構限縮範圍的部門樹。父節點不在清單中的視為根節點。
	 */
	private List<DeptOptionVO> buildScopedOptionTree(List<DeptInfoEntity> filtered) {
		var idSet = filtered.stream().map(DeptInfoEntity::getDeptId).collect(Collectors.toSet());

		Map<Long, List<DeptInfoEntity>> childrenMap = filtered.stream()
			.filter(e -> e.getPid() != null && idSet.contains(e.getPid()))
			.collect(Collectors.groupingBy(DeptInfoEntity::getPid));

		List<DeptOptionVO> roots = new ArrayList<>();
		for (DeptInfoEntity entity : filtered) {
			// 父節點不在 filtered 中 → 視為根節點
			if (entity.getPid() == null || !idSet.contains(entity.getPid())) {
				DeptOptionVO vo = toOptionWithChildren(entity, childrenMap);
				roots.add(vo);
			}
		}
		return roots;
	}

	private DeptOptionVO toOptionWithChildren(DeptInfoEntity entity, Map<Long, List<DeptInfoEntity>> childrenMap) {
		DeptOptionVO vo = DeptOptionVO.builder()
			.value(entity.getDeptId())
			.pid(entity.getPid())
			.label(entity.getDeptName())
			.build();

		List<DeptInfoEntity> children = childrenMap.get(entity.getDeptId());
		if (children != null) {
			vo.setChildren(children.stream()
				.map(child -> toOptionWithChildren(child, childrenMap))
				.collect(Collectors.toList()));
		}
		return vo;
	}

	private DeptDto toDto(DeptInfoEntity entity) {
		return DeptDto.builder()
			.id(entity.getDeptId())
			.pid(entity.getPid())
			.deptName(entity.getDeptName())
			.deptSort(entity.getDeptSort())
			.status(entity.getStatus())
			.hierarchyPath(entity.getHierarchyPath())
			.createBy(entity.getCreateBy())
			.updateBy(entity.getUpdateBy())
			.createTime(entity.getCreateTime())
			.updateTime(entity.getUpdateTime())
			.build();
	}

}
