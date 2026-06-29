package com.taipei.iot.user.service;

import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.user.controller.UserAdminController;
import com.taipei.iot.user.entity.UserInfoLogEntity;
import com.taipei.iot.user.repository.UserInfoLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserAuditServiceTest {

	@Mock
	private UserInfoLogRepository userInfoLogRepository;

	@InjectMocks
	private UserAuditService userAuditService;

	@BeforeEach
	void setUp() {
		// TenantContext may not be set in unit tests — logAction handles null → "SYSTEM"
	}

	@Test
	void logAction_shouldSaveLogEntity() {
		userAuditService.logAction("CREATE", "admin-001", "user-001", "建立帳號");

		ArgumentCaptor<UserInfoLogEntity> captor = ArgumentCaptor.forClass(UserInfoLogEntity.class);
		verify(userInfoLogRepository).save(captor.capture());

		UserInfoLogEntity saved = captor.getValue();
		assertThat(saved.getActionType()).isEqualTo("CREATE");
		assertThat(saved.getActionUserId()).isEqualTo("admin-001");
		assertThat(saved.getTargetUserId()).isEqualTo("user-001");
		assertThat(saved.getDetail()).isEqualTo("建立帳號");
		assertThat(saved.getTenantId()).isEqualTo("SYSTEM"); // no TenantContext set
	}

	@Test
	void logChange_withDiff_shouldIncludeBeforeAfterInDetail() {
		Map<String, String> before = new LinkedHashMap<>();
		before.put("displayName", "Alice");
		before.put("phone", "0912345678");

		Map<String, String> after = new LinkedHashMap<>();
		after.put("displayName", "Bob");
		after.put("phone", "0987654321");

		userAuditService.logChange("UPDATE", "admin-001", "user-001", "管理端更新帳號資料", before, after);

		ArgumentCaptor<UserInfoLogEntity> captor = ArgumentCaptor.forClass(UserInfoLogEntity.class);
		verify(userInfoLogRepository).save(captor.capture());

		String detail = captor.getValue().getDetail();
		assertThat(detail).contains("管理端更新帳號資料");
		assertThat(detail).contains("displayName: Alice → Bob");
		assertThat(detail).contains("phone: 0912345678 → 0987654321");
	}

	@Test
	void logChange_withEmptyBefore_shouldOnlyIncludeSummary() {
		userAuditService.logChange("UPDATE", "admin-001", "user-001", "管理端更新帳號資料", Map.of(), Map.of());

		ArgumentCaptor<UserInfoLogEntity> captor = ArgumentCaptor.forClass(UserInfoLogEntity.class);
		verify(userInfoLogRepository).save(captor.capture());

		assertThat(captor.getValue().getDetail()).isEqualTo("管理端更新帳號資料");
	}

	@Test
	void logChange_withNullBefore_shouldOnlyIncludeSummary() {
		userAuditService.logChange("UPDATE", "admin-001", "user-001", "管理端更新帳號資料", null, Map.of("displayName", "Bob"));

		ArgumentCaptor<UserInfoLogEntity> captor = ArgumentCaptor.forClass(UserInfoLogEntity.class);
		verify(userInfoLogRepository).save(captor.capture());

		assertThat(captor.getValue().getDetail()).isEqualTo("管理端更新帳號資料");
	}

	@Test
	void buildDiffDetail_shouldTruncateAt1000Chars() {
		Map<String, String> before = new LinkedHashMap<>();
		Map<String, String> after = new LinkedHashMap<>();
		// Generate enough entries to exceed 1000 chars
		for (int i = 0; i < 50; i++) {
			before.put("field" + i, "old_value_that_is_long_" + i);
			after.put("field" + i, "new_value_that_is_long_" + i);
		}

		String result = userAuditService.buildDiffDetail("summary", before, after);
		assertThat(result.length()).isLessThanOrEqualTo(1000);
	}

	@Test
	void userAdminController_shouldNotHaveAuditEventAnnotations() {
		// N-10: Verify dual-write elimination — controller must NOT have @AuditEvent
		// since user_info_log (via UserAuditService) is the single source of audit truth.
		long annotatedMethods = Arrays.stream(UserAdminController.class.getDeclaredMethods())
			.filter(m -> m.isAnnotationPresent(AuditEvent.class))
			.count();
		assertThat(annotatedMethods)
			.as("UserAdminController should not have @AuditEvent (use UserAuditService instead)")
			.isZero();
	}

	@Test
	void userModule_shouldNotHaveOwnPageResponse() {
		// N-11: The deprecated user.dto.response.PageResponse must be deleted;
		// all code should use com.taipei.iot.common.dto.PageResponse.
		try {
			Class.forName("com.taipei.iot.user.dto.response.PageResponse");
			throw new AssertionError("user.dto.response.PageResponse still exists — should be deleted (N-11)");
		}
		catch (ClassNotFoundException expected) {
			// expected — class has been deleted
		}
	}

}
