package com.taipei.iot.announcement.service;

import com.taipei.iot.announcement.dto.AnnouncementAttachmentResponse;
import com.taipei.iot.announcement.entity.Announcement;
import com.taipei.iot.announcement.entity.AnnouncementAttachment;
import com.taipei.iot.announcement.repository.AnnouncementAttachmentRepository;
import com.taipei.iot.announcement.repository.AnnouncementDeptRepository;
import com.taipei.iot.announcement.repository.AnnouncementRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.service.FileStorageService;
import com.taipei.iot.common.service.FileValidationService;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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
@DisplayName("AnnouncementAttachmentService — F6 附件管理")
class AnnouncementAttachmentServiceTest {

	private AnnouncementAttachmentService attachmentService;

	@Mock
	private AnnouncementRepository announcementRepository;

	@Mock
	private AnnouncementAttachmentRepository attachmentRepository;

	@Mock
	private AnnouncementDeptRepository announcementDeptRepository;

	@Mock
	private FileStorageService fileStorageService;

	@Mock
	private FileValidationService fileValidationService;

	private static final String DEFAULT_EXT_CONFIG = "pdf";

	@BeforeEach
	void setUp() {
		TenantContext.setCurrentTenantId("TENANT_A");
		// 使用預設設定（僅允許 pdf）建立 service
		attachmentService = new AnnouncementAttachmentService(announcementRepository, attachmentRepository,
				announcementDeptRepository, fileStorageService, fileValidationService, DEFAULT_EXT_CONFIG);
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
		var auth = new UsernamePasswordAuthenticationToken(userId, null,
				List.of(new SimpleGrantedAuthority("ANNOUNCEMENT_MANAGE")));
		auth.setDetails(details);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private Announcement buildAnnouncement(Long id, String createdBy) {
		return Announcement.builder()
			.id(id)
			.tenantId("TENANT_A")
			.title("Test")
			.content("Content")
			.status("PUBLISHED")
			.scope("ALL")
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

	private AnnouncementAttachment buildAttachment(Long id, Long announcementId) {
		return AnnouncementAttachment.builder()
			.id(id)
			.announcementId(announcementId)
			.fileName("document.pdf")
			.fileSize(1024L)
			.mimeType("application/pdf")
			.filePath("announcement/" + announcementId + "/doc.pdf")
			.createdBy("admin-1")
			.createdAt(LocalDateTime.now())
			.build();
	}

	// ═══════════════════════════════════════════════════════════════════════════
	// F6 — 附件管理
	// ═══════════════════════════════════════════════════════════════════════════

	// ── AC-F6-1: 未達上限，上傳 PDF，save() 被呼叫 ──────────────────────────

	@Test
	@DisplayName("[AC-F6-1] upload - 5 個附件時上傳 PDF，save() 被呼叫")
	void AC_F6_1_upload_underLimit_saveCalledAndResponseReturned() {
		setSecurityContext("admin-1", 1L, "ALL");
		Announcement announcement = buildAnnouncement(1L, "admin-1");
		when(announcementRepository.findById(1L)).thenReturn(Optional.of(announcement));
		when(attachmentRepository.findByAnnouncementIdOrderByIdAsc(1L)).thenReturn(List.of()); // 0
																								// attachments
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any())).thenReturn("announcement/1/test.pdf");
		when(attachmentRepository.save(any())).thenAnswer(inv -> {
			AnnouncementAttachment a = inv.getArgument(0);
			a.setId(100L);
			return a;
		});

		MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "pdf-content".getBytes());

		AnnouncementAttachmentResponse resp = attachmentService.upload(1L, file);

		assertNotNull(resp);
		assertEquals(100L, resp.getId());
		verify(attachmentRepository).save(any());
	}

	// ── AC-F6-2: storagePath 來自 FileStorageService.store() ───────────────

	@Test
	@DisplayName("[AC-F6-2] upload - storagePath 由 FileStorageService.store() 回傳")
	void AC_F6_2_upload_storagePathFromStorageService() {
		setSecurityContext("admin-1", 1L, "ALL");
		Announcement announcement = buildAnnouncement(1L, "admin-1");
		when(announcementRepository.findById(1L)).thenReturn(Optional.of(announcement));
		when(attachmentRepository.findByAnnouncementIdOrderByIdAsc(1L)).thenReturn(List.of());
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(eq("announcement/1"), any())).thenReturn("announcement/1/uploaded-file.pdf");
		when(attachmentRepository.save(any())).thenAnswer(inv -> {
			AnnouncementAttachment a = inv.getArgument(0);
			a.setId(101L);
			return a;
		});

		MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());

		attachmentService.upload(1L, file);

		ArgumentCaptor<AnnouncementAttachment> captor = ArgumentCaptor.forClass(AnnouncementAttachment.class);
		verify(attachmentRepository).save(captor.capture());
		assertEquals("announcement/1/uploaded-file.pdf", captor.getValue().getFilePath());
	}

	// ── AC-F6-3: 已達 10 個上限拋錯 ─────────────────────────────────────

	@Test
	@DisplayName("[AC-F6-3] upload - 已達 10 個上限拋 ATTACHMENT_LIMIT_EXCEEDED，store() 未呼叫")
	void AC_F6_3_upload_atLimit_throwsLimitExceeded() {
		setSecurityContext("admin-1", 1L, "ALL");
		Announcement announcement = buildAnnouncement(1L, "admin-1");
		when(announcementRepository.findById(1L)).thenReturn(Optional.of(announcement));
		// 已達 10 個上限
		List<AnnouncementAttachment> existing = List.of(buildAttachment(1L, 1L), buildAttachment(2L, 1L),
				buildAttachment(3L, 1L), buildAttachment(4L, 1L), buildAttachment(5L, 1L), buildAttachment(6L, 1L),
				buildAttachment(7L, 1L), buildAttachment(8L, 1L), buildAttachment(9L, 1L), buildAttachment(10L, 1L));
		when(attachmentRepository.findByAnnouncementIdOrderByIdAsc(1L)).thenReturn(existing);

		MockMultipartFile file = new MockMultipartFile("file", "extra.pdf", "application/pdf", "x".getBytes());

		BusinessException ex = assertThrows(BusinessException.class, () -> attachmentService.upload(1L, file));
		assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
		verify(fileStorageService, never()).store(anyString(), any());
	}

	// ── AC-F6-4: 非白名單副檔名 ─────────────────────────────────────────

	@Test
	@DisplayName("[AC-F6-4] upload - .exe 副檔名拋 FILE_EXTENSION_NOT_ALLOWED")
	void AC_F6_4_upload_disallowedExtension_throwsBeforeValidation() {
		setSecurityContext("admin-1", 1L, "ALL");
		Announcement announcement = buildAnnouncement(1L, "admin-1");
		when(announcementRepository.findById(1L)).thenReturn(Optional.of(announcement));
		when(attachmentRepository.findByAnnouncementIdOrderByIdAsc(1L)).thenReturn(List.of());
		doNothing().when(fileValidationService).validate(any());

		MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/x-msdownload",
				"malware".getBytes());

		BusinessException ex = assertThrows(BusinessException.class, () -> attachmentService.upload(1L, file));
		assertEquals(ErrorCode.FILE_EXTENSION_NOT_ALLOWED, ex.getErrorCode());
	}

	// ── AC-F6-5: config 擴充允許 docx ────────────────────────────────────

	@Test
	@DisplayName("[AC-F6-5] upload - config 擴充允許 docx")
	void AC_F6_5_upload_configuredExtensions_allowsDocx() {
		// 用擴充設定（pdf,docx）重建 service
		AnnouncementAttachmentService customService = new AnnouncementAttachmentService(announcementRepository,
				attachmentRepository, announcementDeptRepository, fileStorageService, fileValidationService,
				"pdf,docx");

		setSecurityContext("admin-1", 1L, "ALL");
		Announcement announcement = buildAnnouncement(1L, "admin-1");
		when(announcementRepository.findById(1L)).thenReturn(Optional.of(announcement));
		when(attachmentRepository.findByAnnouncementIdOrderByIdAsc(1L)).thenReturn(List.of());
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any())).thenReturn("announcement/1/doc.docx");
		when(attachmentRepository.save(any())).thenAnswer(inv -> {
			AnnouncementAttachment a = inv.getArgument(0);
			a.setId(200L);
			return a;
		});

		MockMultipartFile file = new MockMultipartFile("file", "report.docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx-content".getBytes());

		AnnouncementAttachmentResponse resp = customService.upload(1L, file);

		assertNotNull(resp);
		assertEquals(200L, resp.getId());
	}

	// ── AC-F6-6: DEPT_ADMIN 操作他人公告 ─────────────────────────────────

	@Test
	@DisplayName("[AC-F6-6] upload - DEPT_ADMIN 操作他人公告拋 PERMISSION_DENIED")
	void AC_F6_6_upload_deptAdminNotOwner_throwsPermissionDenied() {
		setSecurityContext("dept-admin-1", 5L, "THIS_LEVEL");
		Announcement announcement = buildAnnouncement(1L, "other-admin"); // createdBy ≠
																			// userId
		when(announcementRepository.findById(1L)).thenReturn(Optional.of(announcement));

		MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

		BusinessException ex = assertThrows(BusinessException.class, () -> attachmentService.upload(1L, file));
		assertEquals(ErrorCode.PERMISSION_DENIED, ex.getErrorCode());
	}

	// ── AC-F6-7: virus scan 失敗 ────────────────────────────────────────

	@Test
	@DisplayName("[AC-F6-7] upload - virus scan 失敗拋例外")
	void AC_F6_7_upload_virusScanFails_throwsException() {
		setSecurityContext("admin-1", 1L, "ALL");
		Announcement announcement = buildAnnouncement(1L, "admin-1");
		when(announcementRepository.findById(1L)).thenReturn(Optional.of(announcement));
		when(attachmentRepository.findByAnnouncementIdOrderByIdAsc(1L)).thenReturn(List.of());
		// FileValidationService 拋例外模擬 virus scan 拒絕
		doThrow(new BusinessException(ErrorCode.FILE_VIRUS_DETECTED, "Virus detected!")).when(fileValidationService)
			.validate(any());

		MockMultipartFile file = new MockMultipartFile("file", "malicious.pdf", "application/pdf", "virus".getBytes());

		BusinessException ex = assertThrows(BusinessException.class, () -> attachmentService.upload(1L, file));
		assertEquals(ErrorCode.FILE_VIRUS_DETECTED, ex.getErrorCode());
		verify(fileStorageService, never()).store(anyString(), any());
		verify(attachmentRepository, never()).save(any());
	}

}
