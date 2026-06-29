package com.taipei.iot.platform.announcement.service;

import com.taipei.iot.announcement.service.HtmlSanitizerService;
import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.common.dto.PageResponse;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.util.JwtClaimKeys;
import com.taipei.iot.platform.announcement.dto.PlatformAnnouncementRequest;
import com.taipei.iot.platform.announcement.dto.PlatformAnnouncementResponse;
import com.taipei.iot.platform.announcement.entity.PlatformAnnouncement;
import com.taipei.iot.platform.announcement.repository.PlatformAnnouncementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
class PlatformAnnouncementServiceTest {

	@InjectMocks
	private PlatformAnnouncementService service;

	@Mock
	private PlatformAnnouncementRepository repository;

	@Mock
	private UserRepository userRepository;

	@Spy
	private HtmlSanitizerService htmlSanitizerService = new HtmlSanitizerService();

	@BeforeEach
	void setUp() {
		setSecurityContext("super-admin-1");
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	// ─── helpers ─────────────────────────────────────────────────────────────

	private void setSecurityContext(String userId) {
		Map<String, Object> details = new HashMap<>();
		details.put(JwtClaimKeys.TENANT_ID, (String) null);
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, null,
				List.of(new SimpleGrantedAuthority("PLATFORM_ANNOUNCEMENT_MANAGE")));
		auth.setDetails(details);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	private PlatformAnnouncement buildEntity(Long id, String status, String category, LocalDateTime publishAt,
			LocalDateTime expireAt) {
		return PlatformAnnouncement.builder()
			.id(id)
			.title("Platform Announcement #" + id)
			.content("<p>Content</p>")
			.contentText("Content")
			.status(status)
			.category(category)
			.publishAt(publishAt)
			.expireAt(expireAt)
			.createdBy("super-admin-1")
			.createdByName("Super Admin")
			.createdAt(LocalDateTime.now())
			.build();
	}

	private PlatformAnnouncementRequest buildRequest(String title, String content, String status, String category) {
		return PlatformAnnouncementRequest.builder()
			.title(title)
			.content(content)
			.status(status)
			.category(category)
			.build();
	}

	// ═════════════════════════════════════════════════════════════════════════
	// create()
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class CreateTests {

		@Test
		void create_succeeds() {
			when(userRepository.findById("super-admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("Super Admin").build()));
			when(repository.save(any())).thenAnswer(inv -> {
				PlatformAnnouncement a = inv.getArgument(0);
				a.setId(1L);
				a.setCreatedAt(LocalDateTime.now());
				return a;
			});

			PlatformAnnouncementRequest req = buildRequest("系統維護通知", "<p>將於凌晨 2:00 維護</p>", "PUBLISHED", "MAINTENANCE");

			PlatformAnnouncementResponse resp = service.create(req);

			assertNotNull(resp);
			assertEquals(1L, resp.getId());
			assertEquals("系統維護通知", resp.getTitle());
			assertEquals("MAINTENANCE", resp.getCategory());
			verify(repository).save(any());
		}

		@Test
		void create_published_withoutPublishAt_fillsNow() {
			when(userRepository.findById("super-admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("SA").build()));
			when(repository.save(any())).thenAnswer(inv -> {
				PlatformAnnouncement a = inv.getArgument(0);
				a.setId(2L);
				a.setCreatedAt(LocalDateTime.now());
				return a;
			});

			PlatformAnnouncementRequest req = buildRequest("Test", "<p>Body</p>", "PUBLISHED", "SYSTEM");
			req.setPublishAt(null);

			service.create(req);

			verify(repository).save(argThat(a -> a.getPublishAt() != null));
		}

		@Test
		void create_draft_withoutPublishAt_keepsNull() {
			when(userRepository.findById("super-admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("SA").build()));
			when(repository.save(any())).thenAnswer(inv -> {
				PlatformAnnouncement a = inv.getArgument(0);
				a.setId(3L);
				a.setCreatedAt(LocalDateTime.now());
				return a;
			});

			PlatformAnnouncementRequest req = buildRequest("Draft", "<p>Body</p>", "DRAFT", "SYSTEM");
			req.setPublishAt(null);

			service.create(req);

			verify(repository).save(argThat(a -> a.getPublishAt() == null));
		}

		@Test
		void create_nullCategory_defaultsToSystem() {
			when(userRepository.findById("super-admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("SA").build()));
			when(repository.save(any())).thenAnswer(inv -> {
				PlatformAnnouncement a = inv.getArgument(0);
				a.setId(4L);
				a.setCreatedAt(LocalDateTime.now());
				return a;
			});

			PlatformAnnouncementRequest req = buildRequest("Test", "<p>Body</p>", "DRAFT", null);

			service.create(req);

			verify(repository).save(argThat(a -> "SYSTEM".equals(a.getCategory())));
		}

		@Test
		void create_sanitizesHtmlContent() {
			when(userRepository.findById("super-admin-1"))
				.thenReturn(Optional.of(UserEntity.builder().displayName("SA").build()));
			when(repository.save(any())).thenAnswer(inv -> {
				PlatformAnnouncement a = inv.getArgument(0);
				a.setId(5L);
				a.setCreatedAt(LocalDateTime.now());
				return a;
			});

			PlatformAnnouncementRequest req = buildRequest("XSS Test", "<p>Safe</p><script>alert('xss')</script>",
					"DRAFT", "SYSTEM");

			service.create(req);

			verify(repository).save(argThat(a -> !a.getContent().contains("<script>") && a.getContentText() != null));
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// update()
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class UpdateTests {

		@Test
		void update_succeeds() {
			PlatformAnnouncement existing = buildEntity(1L, "DRAFT", "SYSTEM", null, null);
			when(repository.findById(1L)).thenReturn(Optional.of(existing));
			when(repository.save(any())).thenReturn(existing);

			PlatformAnnouncementRequest req = buildRequest("Updated Title", "<p>Updated</p>", "PUBLISHED",
					"MAINTENANCE");

			PlatformAnnouncementResponse resp = service.update(1L, req);

			assertNotNull(resp);
			assertEquals("Updated Title", existing.getTitle());
			assertEquals("PUBLISHED", existing.getStatus());
			assertEquals("MAINTENANCE", existing.getCategory());
			verify(repository).save(any());
		}

		@Test
		void update_notFound_throwsException() {
			when(repository.findById(99L)).thenReturn(Optional.empty());

			PlatformAnnouncementRequest req = buildRequest("X", "<p>Y</p>", "DRAFT", "SYSTEM");

			BusinessException ex = assertThrows(BusinessException.class, () -> service.update(99L, req));
			assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());
		}

		@Test
		void update_nullCategory_preservesExisting() {
			PlatformAnnouncement existing = buildEntity(1L, "DRAFT", "MAINTENANCE", null, null);
			when(repository.findById(1L)).thenReturn(Optional.of(existing));
			when(repository.save(any())).thenReturn(existing);

			PlatformAnnouncementRequest req = buildRequest("Title", "<p>Body</p>", "DRAFT", null);

			service.update(1L, req);

			verify(repository).save(argThat(a -> "MAINTENANCE".equals(a.getCategory())));
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// delete()
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class DeleteTests {

		@Test
		void delete_succeeds() {
			when(repository.existsById(1L)).thenReturn(true);

			service.delete(1L);

			verify(repository).deleteById(1L);
		}

		@Test
		void delete_notFound_throwsException() {
			when(repository.existsById(99L)).thenReturn(false);

			BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(99L));
			assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// getById()
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class GetByIdTests {

		@Test
		void getById_succeeds() {
			PlatformAnnouncement entity = buildEntity(1L, "PUBLISHED", "SYSTEM", LocalDateTime.now().minusHours(1),
					null);
			when(repository.findById(1L)).thenReturn(Optional.of(entity));

			PlatformAnnouncementResponse resp = service.getById(1L);

			assertNotNull(resp);
			assertEquals(1L, resp.getId());
			assertEquals("PUBLISHED", resp.getStatus());
		}

		@Test
		void getById_notFound_throwsException() {
			when(repository.findById(99L)).thenReturn(Optional.empty());

			BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
			assertEquals(ErrorCode.ANNOUNCEMENT_NOT_FOUND, ex.getErrorCode());
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// listAdmin()
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class ListAdminTests {

		@Test
		void listAdmin_returnsPageResponse() {
			PlatformAnnouncement a1 = buildEntity(1L, "PUBLISHED", "SYSTEM", LocalDateTime.now().minusHours(1), null);
			Page<PlatformAnnouncement> page = new PageImpl<>(List.of(a1));
			when(repository.findAdminList(any(), any(), any(), any(), any())).thenReturn(page);

			PageResponse<PlatformAnnouncementResponse> result = service.listAdmin("ALL", null, null, 0, 10);

			assertNotNull(result);
			assertEquals(1, result.getContent().size());
			assertEquals("SYSTEM", result.getContent().get(0).getCategory());
		}

		@Test
		void listAdmin_passesFilters() {
			Page<PlatformAnnouncement> page = new PageImpl<>(List.of());
			when(repository.findAdminList(any(), any(), any(), any(), any())).thenReturn(page);

			service.listAdmin("DRAFT", "MAINTENANCE", "維護", 0, 10);

			verify(repository).findAdminList(eq("DRAFT"), eq("MAINTENANCE"), eq("維護"), any(), any());
		}

	}

	// ═════════════════════════════════════════════════════════════════════════
	// listPublished()
	// ═════════════════════════════════════════════════════════════════════════

	@Nested
	class ListPublishedTests {

		@Test
		void listPublished_returnsPageResponse() {
			PlatformAnnouncement a1 = buildEntity(1L, "PUBLISHED", "MAINTENANCE", LocalDateTime.now().minusHours(1),
					null);
			Page<PlatformAnnouncement> page = new PageImpl<>(List.of(a1));
			when(repository.findPublished(any(), any(), any())).thenReturn(page);

			PageResponse<PlatformAnnouncementResponse> result = service.listPublished(null, 0, 10);

			assertNotNull(result);
			assertEquals(1, result.getContent().size());
		}

		@Test
		void listPublished_withCategoryFilter() {
			Page<PlatformAnnouncement> page = new PageImpl<>(List.of());
			when(repository.findPublished(any(), any(), any())).thenReturn(page);

			service.listPublished("SYSTEM", 0, 10);

			verify(repository).findPublished(eq("SYSTEM"), any(), any());
		}

	}

}
