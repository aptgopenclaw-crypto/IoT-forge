package com.taipei.iot.common.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.event.VirusScanAuditEvent;
import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FileUploadTemplate [common v2 F-16 / F-3]")
@ExtendWith(MockitoExtension.class)
class FileUploadTemplateTest {

	@Mock
	FileValidationService fileValidationService;

	@Mock
	FileStorageService fileStorageService;

	@Mock
	VirusScanService virusScanService;

	@Mock
	ApplicationEventPublisher eventPublisher;

	@InjectMocks
	FileUploadTemplate template;

	private MockMultipartFile file;

	@BeforeEach
	void setup() {
		file = new MockMultipartFile("file", "report.pdf", "application/pdf", "hello".getBytes());
	}

	@Test
	@DisplayName("正常流程：validate → store → 回傳 Result")
	void happyPathNoScan() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("announcement/1/uuid.pdf");

		FileUploadTemplate.Result result = template.upload(FileUploadTemplate.UploadRequest.builder()
			.file(file)
			.subDir("announcement/1")
			.scanAfterStore(false)
			.build());

		assertThat(result.relativePath()).isEqualTo("announcement/1/uuid.pdf");
		assertThat(result.originalFileName()).isEqualTo("report.pdf");
		assertThat(result.mimeType()).isEqualTo("application/pdf");
		assertThat(result.size()).isEqualTo(5L);
		verify(virusScanService, never()).scan(anyString());
	}

	@Test
	@DisplayName("模組層白名單：副檔名不符 → FILE_EXTENSION_NOT_ALLOWED + 不會 store")
	void moduleWhitelistRejects() {
		doNothing().when(fileValidationService).validate(any());

		assertThatThrownBy(() -> template.upload(FileUploadTemplate.UploadRequest.builder()
			.file(file) // .pdf
			.subDir("x")
			.additionalAllowedExtensions(Set.of("png", "jpg"))
			.build())).isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_EXTENSION_NOT_ALLOWED);

		verify(fileStorageService, never()).store(anyString(), any(MockMultipartFile.class));
	}

	@Test
	@DisplayName("模組層白名單：副檔名符合 → 通過")
	void moduleWhitelistAccepts() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("x/uuid.pdf");

		FileUploadTemplate.Result result = template.upload(FileUploadTemplate.UploadRequest.builder()
			.file(file)
			.subDir("x")
			.additionalAllowedExtensions(Set.of("pdf", "docx"))
			.build());

		assertThat(result.relativePath()).isEqualTo("x/uuid.pdf");
	}

	@Test
	@DisplayName("掃毒 CLEAN：保留檔案、回傳成功")
	void scanCleanKeepsFile() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("x/uuid.pdf");
		when(fileStorageService.resolveAbsolutePath("x/uuid.pdf")).thenReturn("/abs/x/uuid.pdf");
		when(virusScanService.scan("/abs/x/uuid.pdf")).thenReturn(VirusScanService.ScanResult.CLEAN);

		FileUploadTemplate.Result result = template
			.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("x").scanAfterStore(true).build());

		assertThat(result.relativePath()).isEqualTo("x/uuid.pdf");
		verify(fileStorageService, never()).delete(anyString());
	}

	@Test
	@DisplayName("掃毒 INFECTED：刪檔 + FILE_VIRUS_DETECTED")
	void scanInfectedDeletesAndThrows() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("x/uuid.pdf");
		when(fileStorageService.resolveAbsolutePath("x/uuid.pdf")).thenReturn("/abs/x/uuid.pdf");
		when(virusScanService.scan("/abs/x/uuid.pdf")).thenReturn(VirusScanService.ScanResult.INFECTED);

		assertThatThrownBy(() -> template
			.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("x").scanAfterStore(true).build()))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_VIRUS_DETECTED);

		verify(fileStorageService, times(1)).delete("x/uuid.pdf");
	}

	@Test
	@DisplayName("掃毒 ERROR：fail-closed 刪檔 + ATTACHMENT_UPLOAD_FAILED")
	void scanErrorFailClosed() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("x/uuid.pdf");
		when(fileStorageService.resolveAbsolutePath("x/uuid.pdf")).thenReturn("/abs/x/uuid.pdf");
		when(virusScanService.scan("/abs/x/uuid.pdf")).thenReturn(VirusScanService.ScanResult.ERROR);

		assertThatThrownBy(() -> template
			.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("x").scanAfterStore(true).build()))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_UPLOAD_FAILED);

		verify(fileStorageService, times(1)).delete("x/uuid.pdf");
	}

	@Test
	@DisplayName("掃毒擲例外：fail-closed 刪檔 + ATTACHMENT_UPLOAD_FAILED")
	void scanThrowsFailClosed() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("x/uuid.pdf");
		when(fileStorageService.resolveAbsolutePath("x/uuid.pdf")).thenReturn("/abs/x/uuid.pdf");
		when(virusScanService.scan("/abs/x/uuid.pdf")).thenThrow(new RuntimeException("clamd down"));

		assertThatThrownBy(() -> template
			.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("x").scanAfterStore(true).build()))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ATTACHMENT_UPLOAD_FAILED);

		verify(fileStorageService, times(1)).delete("x/uuid.pdf");
	}

	@Test
	@DisplayName("validate 擲例外：不會 store")
	void validationFailureSkipsStore() {
		org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, "too big"))
			.when(fileValidationService)
			.validate(any());

		assertThatThrownBy(
				() -> template.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("x").build()))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_SIZE_EXCEEDED);

		verify(fileStorageService, never()).store(anyString(), any(MockMultipartFile.class));
	}

	// ─── [common v2 F-3] 掃毒結果寫入 audit_log ────────────────────────────────

	@Test
	@DisplayName("F-3：掃毒 CLEAN 不應發出 VirusScanAuditEvent")
	void scanCleanDoesNotPublishAuditEvent() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("x/uuid.pdf");
		when(fileStorageService.resolveAbsolutePath("x/uuid.pdf")).thenReturn("/abs/x/uuid.pdf");
		when(virusScanService.scan("/abs/x/uuid.pdf")).thenReturn(VirusScanService.ScanResult.CLEAN);

		template.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("x").scanAfterStore(true).build());

		verify(eventPublisher, never()).publishEvent(any(VirusScanAuditEvent.class));
	}

	@Test
	@DisplayName("F-3：掃毒 INFECTED 必須發出 VirusScanAuditEvent(INFECTED)")
	void scanInfectedPublishesInfectedAuditEvent() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("ann/9/uuid.pdf");
		when(fileStorageService.resolveAbsolutePath("ann/9/uuid.pdf")).thenReturn("/abs/ann/9/uuid.pdf");
		when(virusScanService.scan("/abs/ann/9/uuid.pdf")).thenReturn(VirusScanService.ScanResult.INFECTED);

		assertThatThrownBy(() -> template
			.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("ann/9").scanAfterStore(true).build()))
			.isInstanceOf(BusinessException.class);

		ArgumentCaptor<VirusScanAuditEvent> captor = ArgumentCaptor.forClass(VirusScanAuditEvent.class);
		verify(eventPublisher, times(1)).publishEvent(captor.capture());
		VirusScanAuditEvent ev = captor.getValue();
		assertThat(ev.result()).isEqualTo(VirusScanAuditEvent.Result.INFECTED);
		assertThat(ev.relativePath()).isEqualTo("ann/9/uuid.pdf");
		assertThat(ev.originalFileName()).isEqualTo("report.pdf");
		assertThat(ev.size()).isEqualTo(5L);
		assertThat(ev.subDir()).isEqualTo("ann/9");
	}

	@Test
	@DisplayName("F-3：掃毒 ERROR 必須發出 VirusScanAuditEvent(ERROR)")
	void scanErrorPublishesErrorAuditEvent() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("x/uuid.pdf");
		when(fileStorageService.resolveAbsolutePath("x/uuid.pdf")).thenReturn("/abs/x/uuid.pdf");
		when(virusScanService.scan("/abs/x/uuid.pdf")).thenReturn(VirusScanService.ScanResult.ERROR);

		assertThatThrownBy(() -> template
			.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("x").scanAfterStore(true).build()))
			.isInstanceOf(BusinessException.class);

		ArgumentCaptor<VirusScanAuditEvent> captor = ArgumentCaptor.forClass(VirusScanAuditEvent.class);
		verify(eventPublisher, times(1)).publishEvent(captor.capture());
		assertThat(captor.getValue().result()).isEqualTo(VirusScanAuditEvent.Result.ERROR);
	}

	@Test
	@DisplayName("F-3：掃毒擲例外（fail-closed）也必須發出 VirusScanAuditEvent(ERROR)")
	void scanThrowsPublishesErrorAuditEvent() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("x/uuid.pdf");
		when(fileStorageService.resolveAbsolutePath("x/uuid.pdf")).thenReturn("/abs/x/uuid.pdf");
		when(virusScanService.scan("/abs/x/uuid.pdf")).thenThrow(new RuntimeException("clamd down"));

		assertThatThrownBy(() -> template
			.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("x").scanAfterStore(true).build()))
			.isInstanceOf(BusinessException.class);

		ArgumentCaptor<VirusScanAuditEvent> captor = ArgumentCaptor.forClass(VirusScanAuditEvent.class);
		verify(eventPublisher, times(1)).publishEvent(captor.capture());
		assertThat(captor.getValue().result()).isEqualTo(VirusScanAuditEvent.Result.ERROR);
	}

	@Test
	@DisplayName("F-3：publishEvent 拋例外時不影響原本 fail-closed 流程（仍丟 BusinessException、仍刪檔）")
	void publishEventFailureDoesNotBreakFailClosedFlow() {
		doNothing().when(fileValidationService).validate(any());
		when(fileStorageService.store(anyString(), any(MockMultipartFile.class))).thenReturn("x/uuid.pdf");
		when(fileStorageService.resolveAbsolutePath("x/uuid.pdf")).thenReturn("/abs/x/uuid.pdf");
		when(virusScanService.scan("/abs/x/uuid.pdf")).thenReturn(VirusScanService.ScanResult.INFECTED);
		org.mockito.Mockito.doThrow(new RuntimeException("listener boom"))
			.when(eventPublisher)
			.publishEvent(any(VirusScanAuditEvent.class));

		assertThatThrownBy(() -> template
			.upload(FileUploadTemplate.UploadRequest.builder().file(file).subDir("x").scanAfterStore(true).build()))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_VIRUS_DETECTED);

		verify(fileStorageService, times(1)).delete("x/uuid.pdf");
	}

}
