package com.taipei.iot.dept.aspect;

import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dept.annotation.DataPermission;
import com.taipei.iot.dept.context.DataScopeContext;
import com.taipei.iot.dept.context.DataScopeFilter;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.enums.DataScopeEnum;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 【預留框架】宣告式資料範圍 AOP — 未來統一資料範圍框架。
 *
 * <p>
 * 設計目標：Service 方法掛 {@code @DataPermission} → 此 Aspect 根據當前使用者的 {@link DataScopeEnum} 自動填入
 * {@link DataScopeContext}，供 Repository 層讀取並 注入查詢條件（dept_id IN / hierarchy_path LIKE）。
 * </p>
 *
 * <p>
 * <b>目前狀態（2026-05）：</b>各模組（user / dept / audit）採手動呼叫
 * {@link com.taipei.iot.dept.service.DataScopeHelper} 的命令式風格控制資料範圍， 因各模組過濾邏輯不一致（audit 按
 * deptId IN、user 用 isDeptInScope 守衛、 announcement 按 owner），尚無法用單一 AOP 涵蓋。
 * </p>
 *
 * <p>
 * 此 AOP 及相關基礎設施（{@link DataPermission}、{@link DataScopeContext}、
 * {@link DataScopeFilter}）保留為日後多模組統一改造時啟用。 當前無任何 Service 方法掛 {@code @DataPermission}，故此
 * Aspect 不會被觸發。
 * </p>
 *
 * @see DataPermission
 * @see DataScopeContext
 * @see DataScopeFilter
 * @see com.taipei.iot.dept.service.DataScopeHelper
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DataPermissionAspect {

	private final DeptInfoRepository deptInfoRepository;

	@Around("@annotation(dataPermission)")
	public Object enforce(ProceedingJoinPoint pjp, DataPermission dataPermission) throws Throwable {
		UserInfo user = SecurityContextUtils.getUserInfo();

		if (user == null || user.getDataScope() == null) {
			// Strict: no user info → inject impossible condition → empty result
			DataScopeContext.set(DataScopeFilter.exact(dataPermission.deptIdField(), -1L));
			try {
				return pjp.proceed();
			}
			finally {
				DataScopeContext.clear();
			}
		}

		DataScopeEnum scope = DataScopeEnum.fromString(user.getDataScope());
		Long deptId = user.getDeptId();

		switch (scope) {
			case ALL:
				// No restriction
				break;

			case THIS_LEVEL:
				if (deptId == null) {
					// Strict: deptId null → empty result (D4 Option B)
					DataScopeContext.set(DataScopeFilter.exact(dataPermission.deptIdField(), -1L));
				}
				else {
					DataScopeContext.set(DataScopeFilter.exact(dataPermission.deptIdField(), deptId));
				}
				break;

			case THIS_LEVEL_AND_BELOW:
				if (deptId == null) {
					// Strict: deptId null → empty result (D4 Option B)
					DataScopeContext.set(DataScopeFilter.exact(dataPermission.deptIdField(), -1L));
				}
				else {
					String hierarchyPath = deptInfoRepository.findById(deptId)
						.map(DeptInfoEntity::getHierarchyPath)
						.orElse(null);

					if (hierarchyPath != null) {
						DataScopeContext
							.set(DataScopeFilter.hierarchyPrefix(dataPermission.hierarchyPathField(), hierarchyPath));
					}
					else {
						// hierarchy_path not found → strict empty result (D4 Option B)
						DataScopeContext.set(DataScopeFilter.exact(dataPermission.deptIdField(), -1L));
					}
				}
				break;
		}

		try {
			return pjp.proceed();
		}
		finally {
			DataScopeContext.clear();
		}
	}

}
