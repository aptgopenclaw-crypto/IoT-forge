package com.taipei.iot.announcement.controller;

import com.taipei.iot.announcement.dto.AnnouncementAttachmentResponse;
import com.taipei.iot.announcement.dto.AnnouncementPinOrderRequest;
import com.taipei.iot.announcement.dto.AnnouncementReadStatsResponse;
import com.taipei.iot.announcement.dto.AnnouncementRequest;
import com.taipei.iot.announcement.dto.AnnouncementResponse;
import com.taipei.iot.announcement.dto.AnnouncementUnreadUserResponse;
import com.taipei.iot.announcement.dto.UnreadCountResponse;
import com.taipei.iot.announcement.service.AnnouncementAttachmentService;
import com.taipei.iot.announcement.service.AnnouncementReadService;
import com.taipei.iot.announcement.service.AnnouncementService;
import com.taipei.iot.audit.annotation.AuditEvent;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.annotation.PaginationParams;
import com.taipei.iot.common.dto.PageQuery;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/v1/auth/announcements")
@RequiredArgsConstructor
@Validated
@Tag(name = "Announcement", description = "公告：前台檢視 / 已讀標記 / 管理端 CRUD")
public class AnnouncementController {

	private final AnnouncementService announcementService;

	private final AnnouncementReadService announcementReadService;

	private final AnnouncementAttachmentService announcementAttachmentService;

	/**
	 * 前台查詢：已發佈 + 未過期 + 受眾範圍符合的公告
	 */
	@GetMapping
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "前台公告列表", description = "回傳已發佈、未過期、且受眾涵蓋當前使用者部門的公告（分頁），可選用 category 過濾")
	public BaseResponse<PageResponse<AnnouncementResponse>> list(@RequestParam(required = false) String category,
			@PaginationParams(defaultSize = 10) PageQuery pageQuery, @RequestParam(required = false) String lang,
			@RequestHeader(name = "Accept-Language", required = false) String acceptLanguage) {
		return BaseResponse.success(announcementService.listVisible(category, pageQuery.getPage(), pageQuery.getSize(),
				resolveLang(lang, acceptLanguage)));
	}

	/**
	 * 管理端查詢：需要 ANNOUNCEMENT_MANAGE 權限
	 */
	@GetMapping("/admin")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@Operation(summary = "管理端公告列表",
			description = "ADMIN 看全部；DEPT_ADMIN 看自建 + 受眾包含自己部門。支援 status / category / keyword 過濾")
	public BaseResponse<PageResponse<AnnouncementResponse>> listAdmin(
			@RequestParam(defaultValue = "ALL") String statusFilter, @RequestParam(required = false) String category,
			@RequestParam(required = false) String keyword, @PaginationParams(defaultSize = 10) PageQuery pageQuery,
			@RequestParam(required = false) String lang,
			@RequestHeader(name = "Accept-Language", required = false) String acceptLanguage) {
		return BaseResponse.success(announcementService.listAdmin(statusFilter, category, keyword, pageQuery.getPage(),
				pageQuery.getSize(), resolveLang(lang, acceptLanguage)));
	}

	/**
	 * 取得單筆公告詳情
	 */
	@GetMapping("/{id}")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "公告詳情", description = "取得單筆公告；具 ANNOUNCEMENT_MANAGE 權限者額外回傳 editable 欄位")
	public BaseResponse<AnnouncementResponse> getById(@PathVariable Long id,
			@RequestParam(required = false) String lang,
			@RequestHeader(name = "Accept-Language", required = false) String acceptLanguage) {
		return BaseResponse
			.success(announcementService.getById(id, hasManagePermission(), resolveLang(lang, acceptLanguage)));
	}

	/**
	 * 取得未讀公告數量
	 */
	@GetMapping("/unread-count")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "未讀公告數量")
	public BaseResponse<UnreadCountResponse> getUnreadCount() {
		return BaseResponse.success(announcementReadService.getUnreadCount());
	}

	/**
	 * 標記某則公告為已讀
	 */
	@PostMapping("/{id}/read")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "標記為已讀", description = "對指定公告寫入已讀記錄；重複呼叫為冪等")
	public BaseResponse<Void> markAsRead(@PathVariable Long id) {
		announcementReadService.markAsRead(id);
		return BaseResponse.success(null);
	}

	/**
	 * 全部標為已讀
	 */
	@PostMapping("/read-all")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "全部標為已讀", description = "將當前使用者所有可見且未讀的公告一次標記為已讀")
	public BaseResponse<Void> markAllAsRead() {
		announcementReadService.markAllAsRead();
		return BaseResponse.success(null);
	}

	/**
	 * 管理端：取得已讀統計（已讀人數 / 受眾總數 / 已讀比例）。
	 * <p>
	 * 對需確認類公告特別有用，可即時掌握傳達率。
	 */
	@GetMapping("/{id}/read-stats")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@Operation(summary = "公告已讀統計", description = "回傳受眾總數、已讀人數、未讀人數與已讀比例")
	public BaseResponse<AnnouncementReadStatsResponse> getReadStats(@PathVariable Long id) {
		return BaseResponse.success(announcementReadService.getReadStats(id));
	}

	/**
	 * 管理端：取得未讀使用者清單（分頁）。
	 */
	@GetMapping("/{id}/unread-users")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@Operation(summary = "公告未讀使用者清單", description = "支援 keyword 模糊比對名稱 / email；分頁回傳")
	public BaseResponse<PageResponse<AnnouncementUnreadUserResponse>> getUnreadUsers(@PathVariable Long id,
			@RequestParam(required = false) String keyword, @PaginationParams PageQuery pageQuery) {
		return BaseResponse
			.success(announcementReadService.getUnreadUsers(id, keyword, pageQuery.getPage(), pageQuery.getSize()));
	}

	/**
	 * 管理端：列出所有置頂公告（依 pin_order 排序），給拖曳排序 UI 使用。
	 */
	@GetMapping("/pinned")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@Operation(summary = "列出置頂公告", description = "依 pin_order 排序，給管理端拖曳排序使用")
	public BaseResponse<List<AnnouncementResponse>> listPinned() {
		return BaseResponse.success(announcementService.listPinned());
	}

	/**
	 * 管理端：依拖曳結果重新指派 pin_order。
	 */
	@PutMapping("/pin-order")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_ANNOUNCEMENT)
	@Operation(summary = "更新置頂順序", description = "依 orderedIds 由前到後重新寫入 pin_order")
	public BaseResponse<Void> reorderPins(@Valid @RequestBody AnnouncementPinOrderRequest request) {
		announcementService.reorderPins(request.getOrderedIds());
		return BaseResponse.success(null);
	}

	/**
	 * 新增公告
	 */
	@PostMapping
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@AuditEvent(AuditEventType.CREATE_ANNOUNCEMENT)
	@Operation(summary = "新增公告")
	public BaseResponse<AnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest request) {
		return BaseResponse.success(announcementService.create(request));
	}

	/**
	 * 編輯公告
	 */
	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_ANNOUNCEMENT)
	@Operation(summary = "編輯公告", description = "請帶上原始 version 進行樂觀鎖檢查；衝突時回傳 409")
	public BaseResponse<AnnouncementResponse> update(@PathVariable Long id,
			@Valid @RequestBody AnnouncementRequest request) {
		return BaseResponse.success(announcementService.update(id, request));
	}

	/**
	 * 刪除公告
	 */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@AuditEvent(AuditEventType.DELETE_ANNOUNCEMENT)
	@Operation(summary = "刪除公告", description = "連動刪除 announcement_depts 與 announcement_reads（DB ON DELETE CASCADE）")
	public BaseResponse<Void> delete(@PathVariable Long id) {
		announcementService.delete(id);
		return BaseResponse.success(null);
	}

	private boolean hasManagePermission() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null)
			return false;
		return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ANNOUNCEMENT_MANAGE"));
	}

	/**
	 * Lang resolution 優先順序：query ?lang= > Accept-Language header > null（service 端
	 * fallback DEFAULT_LANG）。
	 * <p>
	 * 只取 Accept-Language 的第一個 token（忽略 q=），最長 16 字元；service 端在白名單以外會再次 fallback。
	 */
	private String resolveLang(String queryLang, String acceptLanguage) {
		if (queryLang != null && !queryLang.isBlank())
			return queryLang;
		if (acceptLanguage == null || acceptLanguage.isBlank())
			return null;
		// 取首個，忽略 q=
		String first = acceptLanguage.split(",")[0].trim();
		int semi = first.indexOf(';');
		if (semi >= 0)
			first = first.substring(0, semi).trim();
		if (first.length() > 16)
			return null;
		return first.isBlank() ? null : first;
	}

	// ── 附件 ──

	/**
	 * 查詢附件上傳政策（允許副檔名白名單）。 供前端 el-upload accept 與提示訊息使用。
	 */
	@GetMapping("/attachments/config")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "取得公告附件上傳政策", description = "回傳允許副檔名白名單")
	public BaseResponse<List<String>> getAttachmentConfig() {
		return BaseResponse.success(announcementAttachmentService.getAllowedExtensions().stream().sorted().toList());
	}

	/**
	 * 列出公告附件
	 */
	@GetMapping("/{id}/attachments")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "公告附件列表", description = "前台僅能看見已發佈且受眾符合的公告附件；管理員不受限制")
	public BaseResponse<List<AnnouncementAttachmentResponse>> listAttachments(@PathVariable Long id) {
		return BaseResponse.success(announcementAttachmentService.list(id));
	}

	/**
	 * 上傳附件
	 */
	@PostMapping("/{id}/attachments")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_ANNOUNCEMENT)
	@Operation(summary = "上傳公告附件", description = "副檔名 / Magic bytes / 大小校驗由 FileValidationService 處理")
	public BaseResponse<AnnouncementAttachmentResponse> uploadAttachment(@PathVariable Long id,
			@RequestPart("file") MultipartFile file) {
		return BaseResponse.success(announcementAttachmentService.upload(id, file));
	}

	/**
	 * 下載附件
	 */
	@GetMapping("/{id}/attachments/{attachmentId}/download")
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "下載公告附件")
	public ResponseEntity<InputStreamResource> downloadAttachment(@PathVariable Long id,
			@PathVariable Long attachmentId) {
		AnnouncementAttachmentService.DownloadHandle handle = announcementAttachmentService.download(id, attachmentId);
		String encoded = URLEncoder.encode(handle.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
		headers.setContentLength(handle.size());
		// 安全 headers：避免瀏覽器 MIME sniffing 與 inline 執行
		headers.add("X-Content-Type-Options", "nosniff");
		return ResponseEntity.ok()
			.headers(headers)
			.contentType(MediaType.parseMediaType(handle.mimeType()))
			.body(new InputStreamResource(handle.stream()));
	}

	/**
	 * 刪除附件
	 */
	@DeleteMapping("/{id}/attachments/{attachmentId}")
	@PreAuthorize("hasAuthority('ANNOUNCEMENT_MANAGE')")
	@AuditEvent(AuditEventType.UPDATE_ANNOUNCEMENT)
	@Operation(summary = "刪除公告附件")
	public BaseResponse<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
		announcementAttachmentService.delete(id, attachmentId);
		return BaseResponse.success(null);
	}

}
