package com.taipei.iot.announcement.service;

import com.taipei.iot.announcement.dto.AnnouncementRequest;
import com.taipei.iot.announcement.dto.AnnouncementResponse;
import com.taipei.iot.announcement.entity.Announcement;
import com.taipei.iot.announcement.entity.AnnouncementDept;
import com.taipei.iot.announcement.repository.AnnouncementDeptRepository;
import com.taipei.iot.announcement.repository.AnnouncementReadRepository;
import com.taipei.iot.announcement.repository.AnnouncementRepository;

import com.taipei.iot.user.entity.UserEntity;
import com.taipei.iot.user.repository.UserRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AnnouncementService — F1 前台查詢 / F2 管理端 CRUD / F5 置頂排序")
class AnnouncementServiceTest {

	@InjectMocks
	private AnnouncementService announcementService;

	@Mock
	private AnnouncementRepository announcementRepository;

	@Mock
	private AnnouncementDeptRepository announcementDeptRepository;

	@Mock
	private AnnouncementReadRepository announcementReadRepository;

	@Mock
	private DeptInfoRepository deptInfoRepository;

	@Mock
	private UserRepository userRepository;

	@org.mockito.Spy
	private HtmlSanitizerService htmlSanitizerService = new HtmlSanitizerService();

	@Mock
	private AnnouncementAttachmentService attachmentService;

	// ═══════════════════════════════════════════════════════════════════════════
	// Shared fixtures
	// ═══════════════════════════════════════════════════════════════════════════

	@BeforeEach
	void setUp() {
		TenantContext.setCurrentTenantId("TENANT_A");
		when(attachmentService.listByAnnouncementIds(anyList())).thenReturn(Collections.emptyMap());
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	private void setSecurityContext(String userId, Long deptId, String dataScope) {
		Map<String, Object> details = new HashMap<>();
		details.put(JwtClaimKeys.TENANT_ID, "TENANT_A");
		details.put(JwtClaimKeys.DEPT_ID, deptId);
		details.put(JwtClaimKeys.DATA_SCOPE, dataScope);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null,
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
		auth.setDetails(details);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private Announcement buildAnnouncement(Long id, String status, String scope, String createdBy,
			LocalDateTime publishAt, LocalDateTime expireAt) {
		return Announcement.builder()
			.id(id)
			.tenantId("TENANT_A")
			.title("Test #" + id)
			.content("Content")
			.status(status)
			.scope(scope)
			.category("GENERAL")
			.pinned(false)
			.publishAt(publishAt)
			.expireAt(expireAt)
			.createdBy(createdBy)
			.createdByName("User")
			.createdAt(LocalDateTime.now())
			.version(0L)
			.build();
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// F1 — 前台公告查詢 (listVisible / getById)
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("F1 - 前台查詢")
	class FrontendQueryTests {

		// ── AC-F1-1: 3 筆可見公告（ALL + DEPT），含正確 isRead ──────────────

		@Test
		@DisplayName("[AC-F1-1] listVisible - 3 筆可見公告（ALL+DEPT）回傳，含正確 isRead")
		void AC_F1_1_listVisible_mixedScope_returnsThreeWithIsRead() {
			setSecurityContext("user-1", 3L, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusHours(1);
			Announcement a1 = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", past, null);
			Announcement a2 = buildAnnouncement(2L, "PUBLISHED", "DEPT", "admin-1", past, null);
			Announcement a3 = buildAnnouncement(3L, "PUBLISHED", "DEPT", "admin-1", past, null);
			Page<Announcement> page = new PageImpl<>(List.of(a1, a2, a3));
			when(announcementRepository.findVisibleAnnouncements(eq(3L), isNull(), any(), any())).thenReturn(page);
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(List.of(1L, 2L, 3L), "user-1"))
				.thenReturn(List.of());
			when(announcementDeptRepository.findByAnnouncementIdIn(anyList())).thenReturn(List.of());
			when(deptInfoRepository.findByDeptIdIn(any())).thenReturn(List.of());

			PageResponse<AnnouncementResponse> result = announcementService.listVisible(null, 0, 10);

			assertNotNull(result);
			assertEquals(3, result.getContent().size());
		}

		// ── AC-F1-2: 已讀 1 筆 → isRead 正確 ──────────────────────────────

		@Test
		@DisplayName("[AC-F1-2] listVisible - 已讀 1 筆 isRead=true，其餘 false")
		void AC_F1_2_listVisible_oneRead_isReadCorrect() {
			setSecurityContext("user-1", 3L, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusHours(1);
			Announcement a1 = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", past, null);
			Announcement a2 = buildAnnouncement(2L, "PUBLISHED", "DEPT", "admin-1", past, null);
			Page<Announcement> page = new PageImpl<>(List.of(a1, a2));
			when(announcementRepository.findVisibleAnnouncements(anyLong(), isNull(), any(), any())).thenReturn(page);
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(anyList(), eq("user-1"))).thenReturn(List
				.of(new com.taipei.iot.announcement.entity.AnnouncementRead(null, 1L, "user-1", LocalDateTime.now())));
			when(announcementDeptRepository.findByAnnouncementIdIn(anyList())).thenReturn(List.of());
			when(deptInfoRepository.findByDeptIdIn(any())).thenReturn(List.of());

			PageResponse<AnnouncementResponse> result = announcementService.listVisible(null, 0, 10);

			assertEquals(2, result.getContent().size());
			Map<Long, AnnouncementResponse> map = new HashMap<>();
			result.getContent().forEach(r -> map.put(r.getId(), r));
			assertTrue(map.get(1L).getIsRead());
			assertFalse(map.get(2L).getIsRead());
		}

		// ── AC-F1-4: getById 管理員可以看到草稿 ─────────────────────────

		@Test
		@DisplayName("[AC-F1-4] getById - 管理員可看到草稿狀態公告")
		void AC_F1_4_getById_withManagePermission_seesDraft() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement entity = buildAnnouncement(1L, "DRAFT", "ALL", "admin-1", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementResponse resp = announcementService.getById(1L, true);

			assertNotNull(resp);
			assertEquals("DRAFT", resp.getStatus());
		}

		// ── AC-F1-5: deptId=null → -1L ──────────────────────────────────

		@Test
		@DisplayName("[AC-F1-5] listVisible - deptId=null 時傳入 -1L")
		void AC_F1_5_listVisible_nullDeptId_usesMinusOne() {
			setSecurityContext("user-1", null, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusHours(1);
			Announcement a1 = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", past, null);
			Page<Announcement> page = new PageImpl<>(List.of(a1));
			when(announcementRepository.findVisibleAnnouncements(eq(-1L), isNull(), any(), any())).thenReturn(page);
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(anyList(), eq("user-1")))
				.thenReturn(List.of());
			when(announcementDeptRepository.findByAnnouncementIdIn(anyList())).thenReturn(List.of());
			when(deptInfoRepository.findByDeptIdIn(any())).thenReturn(List.of());

			PageResponse<AnnouncementResponse> result = announcementService.listVisible(null, 0, 10);

			assertNotNull(result);
			assertEquals(1, result.getContent().size());
		}

		// ── AC-F1-6: publishAt > now 不出現在前台 ──────────────────────

		@Test
		@DisplayName("[AC-F1-6] listVisible - publishAt > now 的公告不出現在前台")
		void AC_F1_6_listVisible_scheduledAnnouncement_notVisible() {
			setSecurityContext("user-1", 3L, "DEPT");
			// 只有已發佈（過去的）才會回傳；排程中的由 Repository 過濾
			LocalDateTime past = LocalDateTime.now().minusHours(1);
			Announcement a1 = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", past, null);
			Page<Announcement> page = new PageImpl<>(List.of(a1)); // 只有 1 筆
			when(announcementRepository.findVisibleAnnouncements(anyLong(), isNull(), any(), any())).thenReturn(page);
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(anyList(), eq("user-1")))
				.thenReturn(List.of());
			when(announcementDeptRepository.findByAnnouncementIdIn(anyList())).thenReturn(List.of());
			when(deptInfoRepository.findByDeptIdIn(any())).thenReturn(List.of());

			PageResponse<AnnouncementResponse> result = announcementService.listVisible(null, 0, 10);

			assertEquals(1, result.getContent().size());
			// Repository 已過濾排程中公告，service 層僅需確認有傳遞正確參數
			verify(announcementRepository).findVisibleAnnouncements(eq(3L), isNull(), any(), any());
		}

		// ── AC-F1-7: expireAt < now 已過期公告 getById 拋錯 ───────────

		@Test
		@DisplayName("[AC-F1-7] getById - 已過期公告對一般使用者拋 NOT_FOUND")
		void AC_F1_7_getById_expiredAnnouncement_throwsNotFound() {
			setSecurityContext("user-1", 3L, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusDays(10);
			LocalDateTime expired = LocalDateTime.now().minusHours(1);
			Announcement entity = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", past, expired);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.getById(1L, false));
			assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());
		}

		// ── AC-F1-8: getById 不可見公告拋錯 ───────────────────────────

		@Test
		@DisplayName("[AC-F1-8] getById - 草稿對一般使用者拋 NOT_FOUND")
		void AC_F1_8_getById_draftNotVisible_throwsNotFound() {
			setSecurityContext("user-1", 3L, "DEPT");
			Announcement entity = buildAnnouncement(1L, "DRAFT", "ALL", "admin-1", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.getById(1L, false));
			assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());
		}

		// ── AC-F1-9: 非法 category 仍如實傳遞（normalizeCategoryFilter 僅轉 blank/ALL） ─

		@Test
		@DisplayName("[AC-F1-9] listVisible - 非法 category 如實傳遞（僅 blank/ALL 轉 null）")
		void AC_F1_9_listVisible_invalidCategory_passedThrough() {
			setSecurityContext("user-1", 3L, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusHours(1);
			Announcement a1 = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", past, null);
			Page<Announcement> page = new PageImpl<>(List.of(a1));
			// "INVALID" 不會被 normalizeCategoryFilter 轉為 null（僅 blank / ALL 會）
			when(announcementRepository.findVisibleAnnouncements(eq(3L), eq("INVALID"), any(), any())).thenReturn(page);
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(anyList(), eq("user-1")))
				.thenReturn(List.of());
			when(announcementDeptRepository.findByAnnouncementIdIn(anyList())).thenReturn(List.of());
			when(deptInfoRepository.findByDeptIdIn(any())).thenReturn(List.of());

			PageResponse<AnnouncementResponse> result = announcementService.listVisible("INVALID", 0, 10);

			assertNotNull(result);
			verify(announcementRepository).findVisibleAnnouncements(eq(3L), eq("INVALID"), any(), any());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════════
	// F2 — 管理端 CRUD (create / update / delete)
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("F2 - 管理端 CRUD")
	class AdminCrudTests {

		// ── AC-F2-1: ADMIN 新增 scope=ALL ─────────────────────────────────

		@Test
		@DisplayName("[AC-F2-1] create - ADMIN 新增 scope=ALL，createdBy 等於當前 userId")
		void AC_F2_1_create_adminScopeAll_createdByEqualsUserId() {
			setSecurityContext("admin-1", 1L, "ALL");
			when(userRepository.findById("admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("Admin").build()));
			when(announcementRepository.save(any())).thenAnswer(inv -> {
				Announcement a = inv.getArgument(0);
				a.setId(1L);
				return a;
			});
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("New")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(false)
				.build();

			AnnouncementResponse resp = announcementService.create(req);

			assertNotNull(resp);
			verify(announcementRepository)
				.save(argThat(a -> "ALL".equals(a.getScope()) && "admin-1".equals(a.getCreatedBy())));
		}

		// ── AC-F2-2: scope=DEPT 寫入 2 筆 junction ───────────────────────

		@Test
		@DisplayName("[AC-F2-2] create - scope=DEPT targetDeptIds=[10,20] 寫入 2 筆 junction")
		void AC_F2_2_create_deptScope_savesJunctionTwoRows() {
			setSecurityContext("admin-1", 1L, "ALL");
			when(userRepository.findById("admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("Admin").build()));
			when(announcementRepository.save(any())).thenAnswer(inv -> {
				Announcement a = inv.getArgument(0);
				a.setId(10L);
				return a;
			});
			when(announcementDeptRepository.findByAnnouncementId(10L))
				.thenReturn(List.of(AnnouncementDept.builder().announcementId(10L).deptId(10L).build(),
						AnnouncementDept.builder().announcementId(10L).deptId(20L).build()));
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());
			when(deptInfoRepository.findByDeptId(10L)).thenReturn(Optional.of(new DeptInfoEntity()));
			when(deptInfoRepository.findByDeptId(20L)).thenReturn(Optional.of(new DeptInfoEntity()));

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("DeptScope")
				.content("Body")
				.status("PUBLISHED")
				.scope("DEPT")
				.targetDeptIds(List.of(10L, 20L))
				.pinned(false)
				.build();

			AnnouncementResponse resp = announcementService.create(req);

			assertNotNull(resp);
			verify(announcementDeptRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
		}

		// ── AC-F2-4: 正確 version 編輯成功 ──────────────────────────────

		@Test
		@DisplayName("[AC-F2-4] update - 正確 version，saveAndFlush 呼叫一次")
		void AC_F2_4_update_correctVersion_saveAndFlushOnce() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement existing = buildAnnouncement(1L, "DRAFT", "ALL", "admin-1", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));
			when(announcementRepository.saveAndFlush(any())).thenReturn(existing);
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("Updated")
				.content("New body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(true)
				.version(0L)
				.build();

			AnnouncementResponse resp = announcementService.update(1L, req);

			assertNotNull(resp);
			assertEquals("Updated", existing.getTitle());
			verify(announcementRepository).saveAndFlush(any());
		}

		// ── AC-F2-5: 刪除成功 call delete(entity) ─────────────────────────

		@Test
		@DisplayName("[AC-F2-5] delete - ADMIN 刪除呼叫 delete(entity)")
		void AC_F2_5_delete_admin_deleteCalled() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement existing = buildAnnouncement(1L, "PUBLISHED", "ALL", "someone", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));

			announcementService.delete(1L);

			verify(announcementRepository).delete(existing);
		}

		// ── AC-F2-6: DEPT_ADMIN 強制 scope=DEPT ──────────────────────────

		@Test
		@DisplayName("[AC-F2-6] create - DEPT_ADMIN 強制 scope=DEPT, targetDeptIds=[自身部門]")
		void AC_F2_6_create_deptAdmin_forcesDeptscopeAndOwnDept() {
			setSecurityContext("dept-admin-1", 5L, "THIS_LEVEL");
			when(userRepository.findById("dept-admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("DeptAdmin").build()));
			when(announcementRepository.save(any())).thenAnswer(inv -> {
				Announcement a = inv.getArgument(0);
				a.setId(2L);
				return a;
			});
			when(announcementDeptRepository.findByAnnouncementId(2L))
				.thenReturn(List.of(AnnouncementDept.builder().announcementId(2L).deptId(5L).build()));
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());
			DeptInfoEntity deptInfo = new DeptInfoEntity();
			deptInfo.setDeptId(5L);
			deptInfo.setDeptName("Engineering");
			when(deptInfoRepository.findByDeptId(5L)).thenReturn(Optional.of(deptInfo));

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("Dept News")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL") // 前端傳 ALL，後端應強制為 DEPT
				.pinned(false)
				.build();

			AnnouncementResponse resp = announcementService.create(req);

			assertNotNull(resp);
			verify(announcementRepository).save(argThat(a -> "DEPT".equals(a.getScope())));
		}

		// ── AC-F2-7: scope=DEPT 但無 targetDeptIds ───────────────────────

		@Test
		@DisplayName("[AC-F2-7] create - scope=DEPT 無 targetDeptIds 拋 VALIDATION_ERROR")
		void AC_F2_7_create_deptScopeNoTargets_throwsValidationError() {
			setSecurityContext("admin-1", 1L, "ALL");
			when(userRepository.findById("admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("Admin").build()));

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("Dept News")
				.content("Body")
				.status("PUBLISHED")
				.scope("DEPT")
				.targetDeptIds(List.of())
				.pinned(false)
				.build();

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.create(req));
			assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
		}

		// ── AC-F2-8: 編輯無 version ──────────────────────────────────────

		@Test
		@DisplayName("[AC-F2-8] update - 無 version 拋 VALIDATION_ERROR")
		void AC_F2_8_update_missingVersion_throwsValidationError() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement existing = buildAnnouncement(1L, "DRAFT", "ALL", "admin-1", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("NoVersion")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(false)
				.build();

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.update(1L, req));
			assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
		}

		// ── AC-F2-9: version 不符 ────────────────────────────────────────

		@Test
		@DisplayName("[AC-F2-9] update - version 不符拋 ANNOUNCEMENT_VERSION_CONFLICT")
		void AC_F2_9_update_staleVersion_throwsVersionConflict() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement existing = buildAnnouncement(1L, "DRAFT", "ALL", "admin-1", LocalDateTime.now(), null);
			existing.setVersion(1L);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("Stale")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(false)
				.version(0L)
				.build();

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.update(1L, req));
			assertEquals(ErrorCode.ANNOUNCEMENT_VERSION_CONFLICT, ex.getErrorCode());
			verify(announcementRepository, never()).saveAndFlush(any());
		}

		// ── AC-F2-10: XSS sanitize ──────────────────────────────────────

		@Test
		@DisplayName("[AC-F2-10] create - HTML 含 script 被 sanitize 移除")
		void AC_F2_10_create_xssContent_scriptTagRemoved() {
			setSecurityContext("admin-1", 1L, "ALL");
			when(userRepository.findById("admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("Admin").build()));
			when(announcementRepository.save(any())).thenAnswer(inv -> {
				Announcement a = inv.getArgument(0);
				a.setId(1L);
				return a;
			});
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("XSS Test")
				.content("<p>safe</p><script>alert('xss')</script>")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(false)
				.build();

			AnnouncementResponse resp = announcementService.create(req);

			assertNotNull(resp);
			// save 時 content 應已被 sanitize（script 被移除）
			verify(announcementRepository).save(argThat(a -> {
				return !a.getContent().contains("<script>");
			}));
		}

		// ── AC-F2-11: publishAt=null → now() ────────────────────────────

		@Test
		@DisplayName("[AC-F2-11] create - publishAt=null 時設為 LocalDateTime.now()")
		void AC_F2_11_create_nullPublishAt_setsToNow() {
			setSecurityContext("admin-1", 1L, "ALL");
			when(userRepository.findById("admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("Admin").build()));
			when(announcementRepository.save(any())).thenAnswer(inv -> {
				Announcement a = inv.getArgument(0);
				a.setId(1L);
				return a;
			});
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("Now")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(false)
				.publishAt(null)
				.build();

			announcementService.create(req);

			verify(announcementRepository).save(argThat(a -> a.getPublishAt() != null));
		}

		// ── AC-F2-12: 置頂未提供 pinOrder → max+1 ──────────────────────

		@Test
		@DisplayName("[AC-F2-12] create - 置頂未提供 pinOrder 自動 max+1")
		void AC_F2_12_create_pinnedNoOrder_autoMaxPlusOne() {
			setSecurityContext("admin-1", 1L, "ALL");
			when(userRepository.findById("admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("Admin").build()));
			when(announcementRepository.findMaxPinOrder()).thenReturn(5);
			when(announcementRepository.save(any())).thenAnswer(inv -> {
				Announcement a = inv.getArgument(0);
				a.setId(10L);
				return a;
			});
			when(announcementDeptRepository.findByAnnouncementId(10L)).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("Pinned")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(true)
				// pinOrder = null, service 應自動指派
				.build();

			announcementService.create(req);

			verify(announcementRepository).save(argThat(
					a -> Boolean.TRUE.equals(a.getPinned()) && a.getPinOrder() != null && a.getPinOrder() == 6));
		}

		// ── AC-F2-13: pinned=false → pinOrder=null ──────────────────────

		@Test
		@DisplayName("[AC-F2-13] create - pinned=false 時 pinOrder 設為 null")
		void AC_F2_13_create_notPinned_pinOrderIsNull() {
			setSecurityContext("admin-1", 1L, "ALL");
			when(userRepository.findById("admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("Admin").build()));
			when(announcementRepository.save(any())).thenAnswer(inv -> {
				Announcement a = inv.getArgument(0);
				a.setId(1L);
				return a;
			});
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("NotPinned")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(false)
				.pinOrder(3) // 前端可能亂傳，但 service 應清為 null
				.build();

			announcementService.create(req);

			verify(announcementRepository).save(argThat(a -> a.getPinOrder() == null));
		}

		// ── AC-F2-15: DEPT_ADMIN 編輯他人公告 ───────────────────────────

		@Test
		@DisplayName("[AC-F2-15] update - DEPT_ADMIN 編輯他人公告拋 PERMISSION_DENIED")
		void AC_F2_15_update_deptAdminNotOwner_throwsPermissionDenied() {
			setSecurityContext("dept-admin-2", 5L, "THIS_LEVEL");
			Announcement existing = buildAnnouncement(1L, "DRAFT", "DEPT", "other-user", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("Hack")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(false)
				.version(0L)
				.build();

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.update(1L, req));
			assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
		}

		// ── AC-F2-16: DEPT_ADMIN 刪除他人公告 ──────────────────────────

		@Test
		@DisplayName("[AC-F2-16] delete - DEPT_ADMIN 刪除他人公告拋 PERMISSION_DENIED")
		void AC_F2_16_delete_deptAdminNotOwner_throwsPermissionDenied() {
			setSecurityContext("dept-admin-1", 5L, "THIS_LEVEL");
			Announcement existing = buildAnnouncement(1L, "PUBLISHED", "DEPT", "other-user", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.delete(1L));
			assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════════
	// F5 — 置頂排序
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	@DisplayName("F5 - 置頂排序")
	class PinOrderTests {

		// ── AC-F5-1: reorderPins([3,1,2]) ─────────────────────────────────

		@Test
		@DisplayName("[AC-F5-1] reorderPins([3,1,2]) 依序指派 pinOrder=1,2,3")
		void AC_F5_1_reorderPins_assignsPinOrderByIndex() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement a1 = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", LocalDateTime.now(), null);
			a1.setPinned(true);
			a1.setPinOrder(2);
			Announcement a2 = buildAnnouncement(2L, "PUBLISHED", "ALL", "admin-1", LocalDateTime.now(), null);
			a2.setPinned(true);
			a2.setPinOrder(3);
			Announcement a3 = buildAnnouncement(3L, "PUBLISHED", "ALL", "admin-1", LocalDateTime.now(), null);
			a3.setPinned(true);
			a3.setPinOrder(1);
			when(announcementRepository.findAllById(List.of(3L, 1L, 2L))).thenReturn(List.of(a3, a1, a2));

			announcementService.reorderPins(List.of(3L, 1L, 2L));

			assertEquals(1, a3.getPinOrder());
			assertEquals(2, a1.getPinOrder());
			assertEquals(3, a2.getPinOrder());
			verify(announcementRepository).saveAll(anyList());
		}

		// ── AC-F5-2 ADMIN: listPinned 回傳全部 ──────────────────────────

		@Test
		@DisplayName("[AC-F5-2] listPinned - ADMIN 回傳全部置頂公告")
		void AC_F5_2_listPinned_admin_returnsAll() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement a1 = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", LocalDateTime.now(), null);
			a1.setPinned(true);
			when(announcementRepository.findAllPinned()).thenReturn(List.of(a1));
			when(announcementDeptRepository.findByAnnouncementIdIn(anyList())).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());
			when(deptInfoRepository.findByDeptIdIn(any())).thenReturn(List.of());

			List<AnnouncementResponse> result = announcementService.listPinned();

			assertEquals(1, result.size());
			verify(announcementRepository).findAllPinned();
		}

	}

}
