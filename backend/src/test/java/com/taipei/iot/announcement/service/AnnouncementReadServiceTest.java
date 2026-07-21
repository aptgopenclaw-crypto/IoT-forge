package com.taipei.iot.announcement.service;

import com.taipei.iot.announcement.dto.AnnouncementReadStatsResponse;
import com.taipei.iot.announcement.dto.AnnouncementUnreadUserResponse;
import com.taipei.iot.announcement.dto.UnreadCountResponse;
import com.taipei.iot.announcement.entity.Announcement;
import com.taipei.iot.announcement.entity.AnnouncementDept;
import com.taipei.iot.announcement.repository.AnnouncementDeptRepository;
import com.taipei.iot.announcement.repository.AnnouncementReadRepository;
import com.taipei.iot.announcement.repository.AnnouncementRepository;
import com.taipei.iot.announcement.repository.AnnouncementStatsRepository;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.common.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AnnouncementReadService — F3 未讀標記 / F4 已讀統計")
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

	private void setSecurityContextWithScope(String userId, Long deptId, String dataScope) {
		Map<String, Object> details = new HashMap<>();
		details.put(JwtClaimKeys.TENANT_ID, "TENANT_A");
		details.put(JwtClaimKeys.DEPT_ID, deptId);
		details.put(JwtClaimKeys.DATA_SCOPE, dataScope);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null,
				List.of(new SimpleGrantedAuthority("ROLE_USER")));
		auth.setDetails(details);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private Announcement buildAnnouncement(Long id, String scope, String createdBy) {
		return Announcement.builder()
			.id(id)
			.tenantId("TENANT_A")
			.title("Test #" + id)
			.content("Content")
			.status("PUBLISHED")
			.scope(scope)
			.category("GENERAL")
			.pinned(false)
			.publishAt(LocalDateTime.now().minusHours(1))
			.expireAt(LocalDateTime.now().plusDays(10))
			.createdBy(createdBy)
			.createdByName("User")
			.createdAt(LocalDateTime.now())
			.version(0L)
			.build();
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// F3 — 未讀計數與已讀標記
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("F3 - 未讀計數與已讀標記")
	class UnreadMarkingTests {

		// ── AC-F3-1: 3 筆未讀，回傳 count=3 ────────────────────────────────

		@Test
		@DisplayName("[AC-F3-1] getUnreadCount - 3 筆未讀回傳 count=3")
		void AC_F3_1_getUnreadCount_returnsCountFromRepository() {
			setSecurityContext("user-1", 3L);
			when(announcementRepository.countUnread(eq(3L), eq("user-1"), any())).thenReturn(3L);

			UnreadCountResponse resp = announcementReadService.getUnreadCount();

			assertEquals(3, resp.getCount());
		}

		// ── AC-F3-2: markAsRead 正常呼叫 ─────────────────────────────────

		@Test
		@DisplayName("[AC-F3-2] markAsRead - existsById=true, repository 被呼叫")
		void AC_F3_2_markAsRead_announcementExists_repositoryCalled() {
			setSecurityContext("user-1", 3L);
			when(announcementRepository.existsById(42L)).thenReturn(true);

			announcementReadService.markAsRead(42L);

			verify(announcementReadRepository).markAsRead(42L, "user-1");
		}

		// ── AC-F3-3: markAllAsRead 被呼叫，tenantId 來自 TenantContext ────

		@Test
		@DisplayName("[AC-F3-3] markAllAsRead - 正確使用 TenantContext.getCurrentTenantId()")
		void AC_F3_3_markAllAsRead_callsRepositoryWithCorrectParams() {
			setSecurityContext("user-1", 3L);

			announcementReadService.markAllAsRead();

			verify(announcementReadRepository).markAllAsRead("user-1", "TENANT_A", 3L);
		}

		// ── AC-F3-4: deptId=null → -1L ──────────────────────────────────

		@Test
		@DisplayName("[AC-F3-4] getUnreadCount - deptId=null 時傳入 -1L（ArgumentCaptor 驗證）")
		void AC_F3_4_getUnreadCount_nullDeptId_usesMinusOne() {
			setSecurityContext("user-2", null);
			when(announcementRepository.countUnread(anyLong(), anyString(), any())).thenReturn(0L);

			announcementReadService.getUnreadCount();

			verify(announcementRepository).countUnread(eq(-1L), eq("user-2"), any());
		}

		// ── AC-F3-5: 無未讀，count=0 不拋例外 ───────────────────────────

		@Test
		@DisplayName("[AC-F3-5] getUnreadCount - 無未讀回傳 0 不拋例外")
		void AC_F3_5_getUnreadCount_noUnread_returnsZero() {
			setSecurityContext("user-1", 3L);
			when(announcementRepository.countUnread(anyLong(), anyString(), any())).thenReturn(0L);

			UnreadCountResponse resp = announcementReadService.getUnreadCount();

			assertEquals(0, resp.getCount());
		}

		// ── AC-F3-6: 重複呼叫 markAsRead 不拋錯 ────────────────────────

		@Test
		@DisplayName("[AC-F3-6] markAsRead - 重複呼叫兩次不拋錯（冪等）")
		void AC_F3_6_markAsRead_idempotent_noException() {
			setSecurityContext("user-1", 3L);
			when(announcementRepository.existsById(1L)).thenReturn(true);
			doNothing().when(announcementReadRepository).markAsRead(anyLong(), anyString());

			announcementReadService.markAsRead(1L);
			announcementReadService.markAsRead(1L);

			verify(announcementReadRepository, times(2)).markAsRead(1L, "user-1");
		}

		// ── AC-F3-7: 跨租戶防護 ─────────────────────────────────────────

		@Test
		@DisplayName("[AC-F3-7] markAsRead - 跨租戶 ID 拋 ANNOUNCEMENT_NOT_FOUND，native INSERT 不被呼叫")
		void AC_F3_7_markAsRead_crossTenant_throwsNotFound() {
			setSecurityContext("user-1", 3L);
			when(announcementRepository.existsById(999L)).thenReturn(false);

			BusinessException ex = assertThrows(BusinessException.class,
					() -> announcementReadService.markAsRead(999L));
			assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());
			verify(announcementReadRepository, never()).markAsRead(anyLong(), anyString());
		}

		// ── AC-F3-8: markAllAsRead tenantId 以 ArgumentCaptor 驗證 ──────

		@Test
		@DisplayName("[AC-F3-8] markAllAsRead - tenantId 以 ArgumentCaptor 驗證來自 TenantContext")
		void AC_F3_8_markAllAsRead_tenantIdFromContext_captorVerified() {
			TenantContext.setCurrentTenantId("TENANT_B");
			setSecurityContext("user-1", 3L);

			announcementReadService.markAllAsRead();

			ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
			verify(announcementReadRepository).markAllAsRead(eq("user-1"), tenantCaptor.capture(), eq(3L));
			assertEquals("TENANT_B", tenantCaptor.getValue(), "必須以 TenantContext.getCurrentTenantId() 的值呼叫 repository");
			verify(announcementReadRepository, never()).markAllAsRead(anyString(), eq("TENANT_A"), anyLong());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════════
	// F4 — 已讀統計與未讀名單
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("F4 - 已讀統計與未讀名單")
	class ReadStatsTests {

		// ── AC-F4-1: scope=ALL，100/37 已讀，ratio=0.3700 ────────────────

		@Test
		@DisplayName("[AC-F4-1] getReadStats - scope=ALL 100 人 37 已讀，readRatio=0.3700")
		void AC_F4_1_getReadStats_scopeAll_correctRatio() {
			setSecurityContextWithScope("admin-1", 1L, "ALL");
			Announcement entity = buildAnnouncement(1L, "ALL", "admin-1");
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			when(announcementStatsRepository.countAudienceAll("TENANT_A")).thenReturn(100L);
			when(announcementStatsRepository.countReadAll(1L, "TENANT_A")).thenReturn(37L);

			AnnouncementReadStatsResponse resp = announcementReadService.getReadStats(1L);

			assertEquals(100, resp.getTotalAudience());
			assertEquals(37, resp.getReadCount());
			assertEquals(63, resp.getUnreadCount());
			assertEquals(0, BigDecimal.valueOf(0.3700).compareTo(resp.getReadRatio()));
		}

		// ── AC-F4-2: scope=DEPT，50/50 已讀，ratio=1.0000 ────────────────

		@Test
		@DisplayName("[AC-F4-2] getReadStats - scope=DEPT 50/50 已讀，readRatio=1.0000")
		void AC_F4_2_getReadStats_scopeDept_fullReadRatio() {
			setSecurityContextWithScope("admin-1", 1L, "ALL");
			Announcement entity = buildAnnouncement(1L, "DEPT", "admin-1");
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			when(announcementDeptRepository.findByAnnouncementId(1L))
				.thenReturn(List.of(AnnouncementDept.builder().announcementId(1L).deptId(10L).build(),
						AnnouncementDept.builder().announcementId(1L).deptId(20L).build()));
			when(announcementStatsRepository.countAudienceDept("TENANT_A", List.of(10L, 20L))).thenReturn(50L);
			when(announcementStatsRepository.countReadDept(1L, "TENANT_A", List.of(10L, 20L))).thenReturn(50L);

			AnnouncementReadStatsResponse resp = announcementReadService.getReadStats(1L);

			assertEquals(50, resp.getTotalAudience());
			assertEquals(50, resp.getReadCount());
			assertEquals(0, resp.getUnreadCount());
			assertEquals(0, BigDecimal.valueOf(1.0000).compareTo(resp.getReadRatio()));
		}

		// ── AC-F4-3: getUnreadUsers scope=ALL，回傳正確欄位 ────────────

		@Test
		@DisplayName("[AC-F4-3] getUnreadUsers - scope=ALL 回傳正確欄位")
		void AC_F4_3_getUnreadUsers_scopeAll_returnsPagedResult() {
			setSecurityContextWithScope("admin-1", 1L, "ALL");
			Announcement entity = buildAnnouncement(1L, "ALL", "admin-1");
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			Object[] row1 = new Object[] { "u1", "Alice", "alice@test.com", 10L, "Sales" };
			Object[] row2 = new Object[] { "u2", "Bob", "bob@test.com", 20L, "Engineering" };
			Page<Object[]> page = new PageImpl<>(List.of(row1, row2));
			when(announcementStatsRepository.findUnreadUsersAll(eq(1L), eq("TENANT_A"), isNull(), any(Pageable.class)))
				.thenReturn(page);

			PageResponse<AnnouncementUnreadUserResponse> resp = announcementReadService.getUnreadUsers(1L, null, 0, 20);

			assertEquals(2, resp.getContent().size());
			assertEquals("Alice", resp.getContent().get(0).getDisplayName());
			assertEquals("bob@test.com", resp.getContent().get(1).getEmail());
		}

		// ── AC-F4-4: totalAudience=0 → ratio=0 ──────────────────────────

		@Test
		@DisplayName("[AC-F4-4] getReadStats - totalAudience=0 時 readRatio=0，不拋除零例外")
		void AC_F4_4_getReadStats_emptyAudience_ratioIsZero() {
			setSecurityContextWithScope("admin-1", 1L, "ALL");
			Announcement entity = buildAnnouncement(1L, "DEPT", "admin-1");
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			// deptIds 為空（異常情境）
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());

			AnnouncementReadStatsResponse resp = announcementReadService.getReadStats(1L);

			assertEquals(0, resp.getTotalAudience());
			assertEquals(0, BigDecimal.ZERO.compareTo(resp.getReadRatio()));
		}

		// ── AC-F4-5: scope=DEPT deptIds 空 → 回傳空 PageResponse ──────

		@Test
		@DisplayName("[AC-F4-5] getUnreadUsers - scope=DEPT deptIds 空回傳空 PageResponse")
		void AC_F4_5_getUnreadUsers_emptyDeptIds_returnsEmptyPage() {
			setSecurityContextWithScope("admin-1", 1L, "ALL");
			Announcement entity = buildAnnouncement(1L, "DEPT", "admin-1");
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());

			PageResponse<AnnouncementUnreadUserResponse> resp = announcementReadService.getUnreadUsers(1L, null, 0, 20);

			assertTrue(resp.getContent().isEmpty());
			verify(announcementStatsRepository, never()).findUnreadUsersDept(anyLong(), anyString(), anyList(), any(),
					any(Pageable.class));
		}

		// ── AC-F4-6: keyword 空白 → safeKeyword=null ───────────────────

		@Test
		@DisplayName("[AC-F4-6] getUnreadUsers - keyword 空白時 safeKeyword=null（不過濾）")
		void AC_F4_6_getUnreadUsers_blankKeyword_passesNullToRepo() {
			setSecurityContextWithScope("admin-1", 1L, "ALL");
			Announcement entity = buildAnnouncement(1L, "ALL", "admin-1");
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			Page<Object[]> page = new PageImpl<>(List.of());
			when(announcementStatsRepository.findUnreadUsersAll(eq(1L), eq("TENANT_A"), isNull(), any(Pageable.class)))
				.thenReturn(page);

			announcementReadService.getUnreadUsers(1L, "   ", 0, 20);

			verify(announcementStatsRepository).findUnreadUsersAll(eq(1L), eq("TENANT_A"), isNull(),
					any(Pageable.class));
		}

		// ── AC-F4-7: DEPT_ADMIN 查非自建公告 → PERMISSION_DENIED ──────

		@Test
		@DisplayName("[AC-F4-7] getReadStats - DEPT_ADMIN 查非自建公告拋 PERMISSION_DENIED")
		void AC_F4_7_getReadStats_deptAdminNotOwner_throwsPermissionDenied() {
			setSecurityContextWithScope("dept-admin-1", 5L, "THIS_LEVEL");
			Announcement entity = buildAnnouncement(1L, "ALL", "other-admin");
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));

			BusinessException ex = assertThrows(BusinessException.class,
					() -> announcementReadService.getReadStats(1L));
			assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
		}

	}

}
