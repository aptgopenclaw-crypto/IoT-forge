package com.taipei.iot.dept.service;

import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.enums.DataScopeEnum;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 根據當前登入者的 DataScope 判斷可存取的部門範圍。
 */
@Component
@RequiredArgsConstructor
public class DataScopeHelper {

	private final DeptInfoRepository deptInfoRepository;

	/**
	 * 傳回當前使用者可存取的 dept_id 清單。
	 * <ul>
	 * <li>ALL → 回傳 empty（代表不限制）</li>
	 * <li>THIS_LEVEL → 只回傳自己的 deptId</li>
	 * <li>THIS_LEVEL_AND_BELOW → 自己部門 + 所有 hierarchy_path 以自己為前綴的部門</li>
	 * </ul>
	 * @return empty list 表示不限制；non-empty 表示僅限這些 dept_id
	 */
	public List<Long> getVisibleDeptIds() {
		UserInfo user = SecurityContextUtils.getUserInfo();
		if (user == null || user.getDataScope() == null) {
			return Collections.emptyList();
		}

		DataScopeEnum scope = DataScopeEnum.fromString(user.getDataScope());

		if (scope == DataScopeEnum.ALL) {
			return Collections.emptyList();
		}

		Long deptId = user.getDeptId();
		if (deptId == null) {
			// 沒有部門 → 看不到任何資料
			return List.of(-1L);
		}

		if (scope == DataScopeEnum.THIS_LEVEL) {
			return List.of(deptId);
		}

		// THIS_LEVEL_AND_BELOW
		String hierarchyPath = deptInfoRepository.findById(deptId).map(DeptInfoEntity::getHierarchyPath).orElse(null);

		if (hierarchyPath == null) {
			return List.of(deptId);
		}

		// 找出所有 hierarchy_path 以此為前綴的部門（DB LIKE 查詢）
		return deptInfoRepository.findByHierarchyPathStartingWith(hierarchyPath)
			.stream()
			.map(DeptInfoEntity::getDeptId)
			.collect(Collectors.toList());
	}

	/**
	 * 檢查目標 deptId 是否在當前使用者的 DataScope 範圍內。
	 */
	public boolean isDeptInScope(Long targetDeptId) {
		if (targetDeptId == null) {
			return true;
		}
		List<Long> visible = getVisibleDeptIds();
		// empty = ALL，不限制
		if (visible.isEmpty()) {
			return true;
		}
		return visible.contains(targetDeptId);
	}

}
