package com.taipei.iot.announcement.service;

import com.taipei.iot.announcement.dto.AnnouncementRequest;
import com.taipei.iot.announcement.dto.AnnouncementResponse;
import com.taipei.iot.announcement.entity.Announcement;
import com.taipei.iot.announcement.entity.AnnouncementDept;
import com.taipei.iot.announcement.repository.AnnouncementDeptRepository;
import com.taipei.iot.announcement.repository.AnnouncementReadRepository;
import com.taipei.iot.announcement.repository.AnnouncementRepository;
import com.taipei.iot.announcement.repository.AnnouncementTranslationRepository;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.domain.Pageable;
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
	private AnnouncementTranslationRepository announcementTranslationRepository;

	@Mock
	private DeptInfoRepository deptInfoRepository;

	@Mock
	private UserRepository userRepository;

	// 使用真實 sanitizer，避免在每個 create/update 測試手動 stub
	@org.mockito.Spy
	private HtmlSanitizerService htmlSanitizerService = new HtmlSanitizerService();

	@Mock
	private AnnouncementAttachmentService attachmentService;

	@BeforeEach
	void setUp() {
		TenantContext.setCurrentTenantId("TENANT_A");
		// 多語系子表 mock：預設回空集合，避免 NPE；個別測試可覆寫
		when(announcementTranslationRepository.findByAnnouncementId(anyLong())).thenReturn(List.of());
		when(announcementTranslationRepository.findByAnnouncementIdIn(any())).thenReturn(List.of());
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	// ─── Security context helpers ────────────────────────────────────────────

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
	// create()
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	class CreateTests {

		@Test
		void create_asAdmin_scopeAll_succeeds() {
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
			assertEquals("ALL", resp.getScope());
			verify(announcementRepository).save(argThat(a -> "ALL".equals(a.getScope())));
		}

		@Test
		void create_asDeptAdmin_forceScopeToDept() {
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
				.scope("ALL") // 前端可能傳 ALL，但後端應強制為 DEPT
				.pinned(false)
				.build();

			AnnouncementResponse resp = announcementService.create(req);

			assertNotNull(resp);
			// 驗證 save 時 scope 被強制設為 DEPT
			verify(announcementRepository).save(argThat(a -> "DEPT".equals(a.getScope())));
			// 驗證 junction table 只存自己部門
			verify(announcementDeptRepository).saveAll(argThat(list -> ((List<?>) list).size() == 1));
		}

		@Test
		void create_deptScope_withoutDeptIds_throwsValidationError() {
			setSecurityContext("admin-1", 1L, "ALL");
			when(userRepository.findById("admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("Admin").build()));

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("Dept News")
				.content("Body")
				.status("PUBLISHED")
				.scope("DEPT")
				.targetDeptIds(List.of()) // 空 list
				.pinned(false)
				.build();

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.create(req));
			assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════════
	// update()
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	class UpdateTests {

		@Test
		void update_asAdmin_succeeds() {
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
			assertEquals("PUBLISHED", existing.getStatus());
		}

		@Test
		void update_asDeptAdmin_notOwner_throwsPermissionDenied() {
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

		@Test
		void update_notFound_throwsException() {
			setSecurityContext("admin-1", 1L, "ALL");
			when(announcementRepository.findById(99L)).thenReturn(Optional.empty());

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("X")
				.content("Y")
				.status("DRAFT")
				.scope("ALL")
				.version(0L)
				.build();

			assertThrows(BusinessException.class, () -> announcementService.update(99L, req));
		}

		@Test
		void update_staleVersion_throwsVersionConflict() {
			// 客戶端送出舊版本（0），但 DB 已被別人更新成 1
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

		@Test
		void update_missingVersion_throwsValidationError() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement existing = buildAnnouncement(1L, "DRAFT", "ALL", "admin-1", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("NoVersion")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(false)
				.build(); // 無 version

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.update(1L, req));
			assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
		}

		@Test
		void update_optimisticLockingFailureAtFlush_translatesToVersionConflict() {
			// 模擬 load 通過後、flush 時 Hibernate 偵測到 race 拋 OptimisticLockingFailureException
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement existing = buildAnnouncement(1L, "DRAFT", "ALL", "admin-1", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));
			when(announcementRepository.saveAndFlush(any()))
				.thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Announcement.class, 1L));

			AnnouncementRequest req = AnnouncementRequest.builder()
				.title("Race")
				.content("Body")
				.status("PUBLISHED")
				.scope("ALL")
				.pinned(false)
				.version(0L)
				.build();

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.update(1L, req));
			assertEquals(ErrorCode.ANNOUNCEMENT_VERSION_CONFLICT, ex.getErrorCode());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════════
	// delete()
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	class DeleteTests {

		@Test
		void delete_asAdmin_succeeds() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement existing = buildAnnouncement(1L, "PUBLISHED", "ALL", "someone", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));

			announcementService.delete(1L);

			verify(announcementRepository).delete(existing);
		}

		@Test
		void delete_asDeptAdmin_notOwner_throwsPermissionDenied() {
			setSecurityContext("dept-admin-1", 5L, "THIS_LEVEL");
			Announcement existing = buildAnnouncement(1L, "PUBLISHED", "DEPT", "other-user", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.delete(1L));
			assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
		}

		@Test
		void delete_asDeptAdmin_owner_succeeds() {
			setSecurityContext("dept-admin-1", 5L, "THIS_LEVEL");
			Announcement existing = buildAnnouncement(1L, "PUBLISHED", "DEPT", "dept-admin-1", LocalDateTime.now(),
					null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(existing));

			announcementService.delete(1L);

			verify(announcementRepository).delete(existing);
		}

	}

	// ═══════════════════════════════════════════════════════════════════════════
	// getById() - 可見性檢查
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	class GetByIdTests {

		@Test
		void getById_withManagePermission_canSeeDraft() {
			setSecurityContext("admin-1", 1L, "ALL");
			Announcement entity = buildAnnouncement(1L, "DRAFT", "ALL", "admin-1", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementResponse resp = announcementService.getById(1L, true);

			assertNotNull(resp);
			assertEquals("DRAFT", resp.getStatus());
		}

		@Test
		void getById_withoutPermission_publishedVisible_succeeds() {
			setSecurityContext("user-1", 3L, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusHours(1);
			LocalDateTime future = LocalDateTime.now().plusDays(10);
			Announcement entity = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", past, future);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			when(announcementDeptRepository.findByAnnouncementId(1L)).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementResponse resp = announcementService.getById(1L, false);

			assertNotNull(resp);
		}

		@Test
		void getById_withoutPermission_draftNotVisible_throwsNotFound() {
			setSecurityContext("user-1", 3L, "DEPT");
			Announcement entity = buildAnnouncement(1L, "DRAFT", "ALL", "admin-1", LocalDateTime.now(), null);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.getById(1L, false));
			assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());
		}

		@Test
		void getById_withoutPermission_expired_throwsNotFound() {
			setSecurityContext("user-1", 3L, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusDays(10);
			LocalDateTime expired = LocalDateTime.now().minusHours(1);
			Announcement entity = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", past, expired);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.getById(1L, false));
			assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());
		}

		@Test
		void getById_withoutPermission_deptScope_notInDept_throwsNotFound() {
			setSecurityContext("user-1", 3L, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusHours(1);
			LocalDateTime future = LocalDateTime.now().plusDays(10);
			Announcement entity = buildAnnouncement(1L, "PUBLISHED", "DEPT", "admin-1", past, future);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			when(announcementDeptRepository.existsByAnnouncementIdAndDeptId(1L, 3L)).thenReturn(false);

			BusinessException ex = assertThrows(BusinessException.class, () -> announcementService.getById(1L, false));
			assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());
		}

		@Test
		void getById_withoutPermission_deptScope_inDept_succeeds() {
			setSecurityContext("user-1", 3L, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusHours(1);
			LocalDateTime future = LocalDateTime.now().plusDays(10);
			Announcement entity = buildAnnouncement(1L, "PUBLISHED", "DEPT", "admin-1", past, future);
			when(announcementRepository.findById(1L)).thenReturn(Optional.of(entity));
			when(announcementDeptRepository.existsByAnnouncementIdAndDeptId(1L, 3L)).thenReturn(true);
			when(announcementDeptRepository.findByAnnouncementId(1L))
				.thenReturn(List.of(AnnouncementDept.builder().announcementId(1L).deptId(3L).build()));
			DeptInfoEntity deptInfo = new DeptInfoEntity();
			deptInfo.setDeptId(3L);
			deptInfo.setDeptName("Sales");
			when(deptInfoRepository.findByDeptId(3L)).thenReturn(Optional.of(deptInfo));
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());

			AnnouncementResponse resp = announcementService.getById(1L, false);

			assertNotNull(resp);
			assertEquals("PUBLISHED", resp.getStatus());
		}

		@Test
		void getById_notFound_throwsException() {
			setSecurityContext("user-1", 1L, "ALL");
			when(announcementRepository.findById(99L)).thenReturn(Optional.empty());

			assertThrows(BusinessException.class, () -> announcementService.getById(99L, false));
		}

	}

	// ═══════════════════════════════════════════════════════════════════════════
	// listVisible()
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	class ListVisibleTests {

		@Test
		void listVisible_returnsPageResponse() {
			setSecurityContext("user-1", 3L, "DEPT");
			LocalDateTime past = LocalDateTime.now().minusHours(1);
			Announcement a1 = buildAnnouncement(1L, "PUBLISHED", "ALL", "admin-1", past, null);
			Page<Announcement> page = new PageImpl<>(List.of(a1));
			when(announcementRepository.findVisibleAnnouncements(eq(3L), isNull(), any(), any())).thenReturn(page);
			when(announcementDeptRepository.findByAnnouncementIdIn(List.of(1L))).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), eq("user-1"))).thenReturn(List.of());
			when(deptInfoRepository.findByDeptIdIn(any())).thenReturn(List.of());

			PageResponse<AnnouncementResponse> result = announcementService.listVisible(null, 0, 10);

			assertNotNull(result);
			assertEquals(1, result.getContent().size());
			assertEquals("Test #1", result.getContent().get(0).getTitle());
		}

	}

	// ═══════════════════════════════════════════════════════════════════════════
	// listAdmin()
	// ═══════════════════════════════════════════════════════════════════════════

	@Nested
	class ListAdminTests {

		@Test
		void listAdmin_asAdmin_queriesAll() {
			setSecurityContext("admin-1", 1L, "ALL");
			Page<Announcement> page = new PageImpl<>(List.of());
			when(announcementRepository.findAdminAnnouncements(any(), any(), any(), any(), any())).thenReturn(page);

			PageResponse<AnnouncementResponse> result = announcementService.listAdmin("ALL", null, null, 0, 10);

			assertNotNull(result);
			verify(announcementRepository).findAdminAnnouncements(eq("ALL"), isNull(), isNull(), any(), any());
		}

		@Test
		void listAdmin_asDeptAdmin_queriesDeptScoped() {
			setSecurityContext("dept-admin-1", 5L, "THIS_LEVEL");
			Page<Announcement> page = new PageImpl<>(List.of());
			when(announcementRepository.findDeptAdminAnnouncements(any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(page);
			when(announcementDeptRepository.findByAnnouncementIdIn(any())).thenReturn(List.of());
			when(announcementReadRepository.findByAnnouncementIdInAndUserId(any(), any())).thenReturn(List.of());
			when(deptInfoRepository.findByDeptIdIn(any())).thenReturn(List.of());

			PageResponse<AnnouncementResponse> result = announcementService.listAdmin("ALL", null, null, 0, 10);

			assertNotNull(result);
			verify(announcementRepository).findDeptAdminAnnouncements(eq("dept-admin-1"), eq(5L), eq("ALL"), isNull(),
					isNull(), any(), any());
		}

		@Test
		void listAdmin_keywordEscaped() {
			setSecurityContext("admin-1", 1L, "ALL");
			Page<Announcement> page = new PageImpl<>(List.of());
			when(announcementRepository.findAdminAnnouncements(any(), any(), any(), any(), any())).thenReturn(page);

			announcementService.listAdmin("ALL", null, "50%_off", 0, 10);

			verify(announcementRepository).findAdminAnnouncements(eq("ALL"), isNull(), eq("%50\\%\\_off%"), any(),
					any());
		}

		@Test
		void listAdmin_categoryFilter_passedThrough() {
			setSecurityContext("admin-1", 1L, "ALL");
			Page<Announcement> page = new PageImpl<>(List.of());
			when(announcementRepository.findAdminAnnouncements(any(), any(), any(), any(), any())).thenReturn(page);

			announcementService.listAdmin("ALL", "MAINTENANCE", null, 0, 10);

			verify(announcementRepository).findAdminAnnouncements(eq("ALL"), eq("MAINTENANCE"), isNull(), any(), any());
		}

		@Test
		void listAdmin_categoryAll_normalizedToNull() {
			setSecurityContext("admin-1", 1L, "ALL");
			Page<Announcement> page = new PageImpl<>(List.of());
			when(announcementRepository.findAdminAnnouncements(any(), any(), any(), any(), any())).thenReturn(page);

			announcementService.listAdmin("ALL", "ALL", null, 0, 10);

			verify(announcementRepository).findAdminAnnouncements(eq("ALL"), isNull(), isNull(), any(), any());
		}

	}

}
