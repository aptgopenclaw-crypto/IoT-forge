package com.taipei.iot.user.service;

import com.taipei.iot.tenant.TenantContext;
import com.taipei.iot.user.entity.UserInfoLogEntity;
import com.taipei.iot.user.repository.UserInfoLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class UserAuditService {

	private final UserInfoLogRepository userInfoLogRepository;

	public void logAction(String actionType, String actionUserId, String targetUserId, String detail) {
		String tenantId = TenantContext.getCurrentTenantId();
		if (tenantId == null) {
			tenantId = "SYSTEM";
		}

		UserInfoLogEntity log = UserInfoLogEntity.builder()
			.tenantId(tenantId)
			.actionType(actionType)
			.actionUserId(actionUserId)
			.targetUserId(targetUserId)
			.detail(detail)
			// [Phase B] SUPER_ADMIN 代操標記（一般操作為 null）
			.impersonatedBy(TenantContext.getImpersonator())
			.build();
		userInfoLogRepository.save(log);
	}

	/**
	 * Log a change action with before/after diff encoded in the detail field.
	 * @param actionType action type code (e.g. "UPDATE")
	 * @param actionUserId who performed the action
	 * @param targetUserId who is affected
	 * @param summary human-readable description
	 * @param before field values before change (only changed fields)
	 * @param after field values after change (only changed fields)
	 */
	public void logChange(String actionType, String actionUserId, String targetUserId, String summary,
			Map<String, String> before, Map<String, String> after) {
		String detail = buildDiffDetail(summary, before, after);
		logAction(actionType, actionUserId, targetUserId, detail);
	}

	String buildDiffDetail(String summary, Map<String, String> before, Map<String, String> after) {
		if (before == null || before.isEmpty()) {
			return summary;
		}
		StringJoiner sj = new StringJoiner("; ", summary + " [", "]");
		for (Map.Entry<String, String> entry : after.entrySet()) {
			String key = entry.getKey();
			String oldVal = before.getOrDefault(key, "");
			String newVal = entry.getValue() != null ? entry.getValue() : "";
			if (!oldVal.equals(newVal)) {
				sj.add(key + ": " + oldVal + " → " + newVal);
			}
		}
		String result = sj.toString();
		return result.length() > 1000 ? result.substring(0, 1000) : result;
	}

}
