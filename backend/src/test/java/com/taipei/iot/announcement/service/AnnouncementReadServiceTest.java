package com.taipei.iot.announcement.service;

import com.taipei.iot.announcement.dto.UnreadCountResponse;
import com.taipei.iot.announcement.repository.AnnouncementDeptRepository;
import com.taipei.iot.announcement.repository.AnnouncementReadRepository;
import com.taipei.iot.announcement.repository.AnnouncementRepository;
import com.taipei.iot.announcement.repository.AnnouncementStatsRepository;
import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnnouncementReadServiceTest {

	@InjectMocks
	private AnnouncementReadService announcementReadService;

	@Mock
	private AnnouncementRepository announcementRepository;

	@Mock
	private AnnouncementReadRepository announcementReadRepository;

	@Mock
	private AnnouncementDeptRepository announcementDeptRepository;

	@Mock
	private AnnouncementStatsRepository announcementStatsRepository;

	@BeforeEach
	void setUp() {
		TenantContext.setCurrentTenantId("TENANT_A");
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	private void setSecurityContext(String userId, Long deptId) {
		Map<String, Object> details = new HashMap<>();
		details.put(JwtClaimKeys.TENANT_ID, "TENANT_A");
		details.put(JwtClaimKeys.DEPT_ID, deptId);
		details.put(JwtClaimKeys.DATA_SCOPE, "DEPT");
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null,
				List.of(new SimpleGrantedAuthority("ROLE_USER")));
		auth.setDetails(details);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	// ─── getUnreadCount ─────────────────────────────────────────────────────

	@Test
	void getUnreadCount_returnsCountFromRepository() {
		setSecurityContext("user-1", 3L);
		when(announcementRepository.countUnread(eq(3L), eq("user-1"), any())).thenReturn(5L);

		UnreadCountResponse resp = announcementReadService.getUnreadCount();

		assertEquals(5, resp.getCount());
	}

	@Test
	void getUnreadCount_nullDeptId_usesMinusOne() {
		setSecurityContext("user-2", null);
		// UserInfo.getDeptId() returns null → service uses -1L
		when(announcementRepository.countUnread(eq(-1L), eq("user-2"), any())).thenReturn(0L);

		UnreadCountResponse resp = announcementReadService.getUnreadCount();

		assertEquals(0, resp.getCount());
		verify(announcementRepository).countUnread(eq(-1L), eq("user-2"), any());
	}

	// ─── markAsRead ─────────────────────────────────────────────────────────

	@Test
	void markAsRead_callsRepositoryUpsert() {
		setSecurityContext("user-1", 3L);
		when(announcementRepository.existsById(42L)).thenReturn(true);

		announcementReadService.markAsRead(42L);

		verify(announcementReadRepository).markAsRead(42L, "user-1");
	}

	@Test
	void markAsRead_idempotent_noException() {
		setSecurityContext("user-1", 3L);
		when(announcementRepository.existsById(1L)).thenReturn(true);
		doNothing().when(announcementReadRepository).markAsRead(anyLong(), anyString());

		// 呼叫兩次不應拋例外（ON CONFLICT DO NOTHING）
		announcementReadService.markAsRead(1L);
		announcementReadService.markAsRead(1L);

		verify(announcementReadRepository, times(2)).markAsRead(1L, "user-1");
	}

	@Test
	void markAsRead_crossTenantAnnouncementId_throwsNotFound() {
		// 模擬攻擊情境：使用者送出另一個租戶的公告 ID
		// tenant-filtered repository 應回傳 false (查不到)，service 必須擋下
		setSecurityContext("user-1", 3L);
		when(announcementRepository.existsById(999L)).thenReturn(false);

		BusinessException ex = assertThrows(BusinessException.class, () -> announcementReadService.markAsRead(999L));
		assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());

		// 關鍵：native INSERT 不可被呼叫，防止跨租戶寫入
		verify(announcementReadRepository, never()).markAsRead(anyLong(), anyString());
	}

	// ─── markAllAsRead ──────────────────────────────────────────────────────

	@Test
	void markAllAsRead_callsRepositoryWithCorrectParams() {
		setSecurityContext("user-1", 3L);

		announcementReadService.markAllAsRead();

		verify(announcementReadRepository).markAllAsRead("user-1", "TENANT_A", 3L);
	}

	@Test
	void markAllAsRead_nullDeptId_usesMinusOne() {
		setSecurityContext("user-2", null);

		announcementReadService.markAllAsRead();

		verify(announcementReadRepository).markAllAsRead("user-2", "TENANT_A", -1L);
	}

	/**
	 * 迴歸測試：確保 markAllAsRead 嚴格使用「當前 TenantContext」的 tenantId， 不會將其他租戶的公告寫入當前使用者的 reads。
	 *
	 * native SQL 內有 {@code WHERE a.tenant_id = :tenantId} 作為最後防線， 本測試以 ArgumentCaptor 鎖定
	 * service 傳入的 tenantId 必須等於 TenantContext， 防止未來程式碼誤改成從 UserInfo / SecurityContext /
	 * hard-coded 取得 tenantId 而導致跨租戶寫入退步。
	 */
	@Test
	void markAllAsRead_usesTenantContextStrictly_noCrossTenantLeak() {
		// Arrange: TenantContext = TENANT_B（覆蓋 setUp 的 TENANT_A）
		TenantContext.setCurrentTenantId("TENANT_B");
		setSecurityContext("user-1", 3L);

		// Act
		announcementReadService.markAllAsRead();

		// Assert: 必須以 TENANT_B 呼叫，且絕對不能用 TENANT_A 或任何其他值
		org.mockito.ArgumentCaptor<String> tenantCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(announcementReadRepository).markAllAsRead(eq("user-1"), tenantCaptor.capture(), eq(3L));
		assertEquals("TENANT_B", tenantCaptor.getValue(),
				"markAllAsRead 必須以當前 TenantContext 的 tenantId 呼叫 repository，" + "native SQL 才能正確過濾跨租戶公告");

		// 額外確認：絕無任何呼叫帶其他租戶 ID
		verify(announcementReadRepository, never()).markAllAsRead(anyString(), eq("TENANT_A"), anyLong());
		verify(announcementReadRepository, never()).markAllAsRead(anyString(), eq("SYSTEM"), anyLong());
		verify(announcementReadRepository, never()).markAllAsRead(anyString(), isNull(), anyLong());
	}

	/**
	 * 迴歸測試：TenantContext 未設定（被誤清空）時， markAllAsRead 不應該以 null tenantId 呼叫 native SQL （這會導致
	 * WHERE tenant_id = NULL 永遠 false，雖然安全但代表呼叫鏈缺陷）。
	 *
	 * 此測試標示「未設定 tenant context」屬於需要被偵測的狀態： 目前實作會傳入 null → 由 PostgreSQL 端產出空結果（安全
	 * fail-closed）。 若日後改為主動拋例外，更新此測試即可。
	 */
	@Test
	void markAllAsRead_withoutTenantContext_passesNullAndInsertsNothing() {
		TenantContext.clear();
		setSecurityContext("user-1", 3L);

		announcementReadService.markAllAsRead();

		// fail-closed：tenantId = null，native SQL 的 WHERE 條件永遠不成立
		verify(announcementReadRepository).markAllAsRead("user-1", null, 3L);
	}

}
