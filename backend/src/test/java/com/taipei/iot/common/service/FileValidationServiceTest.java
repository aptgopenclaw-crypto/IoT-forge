package com.taipei.iot.common.service;

import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileValidationServiceTest {

	// 圖片 5MB, 文件 20MB, 影音 100MB
	private final FileValidationService service = new FileValidationService(5 * 1024 * 1024, 20 * 1024 * 1024,
			100 * 1024 * 1024);

	// ── 副檔名白名單 ──

	@Test
	void validateExtension_jpg_allowed() {
		assertEquals("jpg", service.validateExtension("photo.jpg"));
	}

	@Test
	void validateExtension_jpeg_allowed() {
		assertEquals("jpeg", service.validateExtension("photo.jpeg"));
	}

	@Test
	void validateExtension_pdf_allowed() {
		assertEquals("pdf", service.validateExtension("report.pdf"));
	}

	@Test
	void validateExtension_xlsx_allowed() {
		assertEquals("xlsx", service.validateExtension("data.xlsx"));
	}

	@Test
	void validateExtension_exe_rejected() {
		BusinessException ex = assertThrows(BusinessException.class, () -> service.validateExtension("malware.exe"));
		assertEquals(ErrorCode.FILE_EXTENSION_NOT_ALLOWED, ex.getErrorCode());
	}

	@Test
	void validateExtension_php_rejected() {
		assertThrows(BusinessException.class, () -> service.validateExtension("shell.php"));
	}

	@Test
	void validateExtension_jsp_rejected() {
		assertThrows(BusinessException.class, () -> service.validateExtension("webshell.jsp"));
	}

	@Test
	void validateExtension_noExtension_rejected() {
		assertThrows(BusinessException.class, () -> service.validateExtension("noext"));
	}

	@Test
	void validateExtension_null_rejected() {
		assertThrows(BusinessException.class, () -> service.validateExtension(null));
	}

	@Test
	void validateExtension_caseInsensitive() {
		assertEquals("png", service.validateExtension("image.PNG"));
	}

	// ── 檔案大小（按類別） ──

	@Test
	void validateSize_imageWithinLimit_ok() {
		assertDoesNotThrow(() -> service.validateSize(4 * 1024 * 1024, "jpg")); // 4MB <
																				// 5MB
	}

	@Test
	void validateSize_imageExceedsLimit_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.validateSize(6 * 1024 * 1024, "png")); // 6MB > 5MB
		assertEquals(ErrorCode.FILE_SIZE_EXCEEDED, ex.getErrorCode());
	}

	@Test
	void validateSize_imageExactlyAtLimit_ok() {
		assertDoesNotThrow(() -> service.validateSize(5 * 1024 * 1024, "webp")); // 5MB ==
																					// 5MB
	}

	@Test
	void validateSize_documentWithinLimit_ok() {
		assertDoesNotThrow(() -> service.validateSize(15 * 1024 * 1024, "pdf")); // 15MB <
																					// 20MB
	}

	@Test
	void validateSize_documentExceedsLimit_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.validateSize(21 * 1024 * 1024, "xlsx")); // 21MB > 20MB
		assertEquals(ErrorCode.FILE_SIZE_EXCEEDED, ex.getErrorCode());
	}

	@Test
	void validateSize_mediaWithinLimit_ok() {
		assertDoesNotThrow(() -> service.validateSize(80 * 1024 * 1024, "mp4")); // 80MB <
																					// 100MB
	}

	@Test
	void validateSize_mediaExceedsLimit_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.validateSize(101L * 1024 * 1024, "mp4")); // 101MB > 100MB
		assertEquals(ErrorCode.FILE_SIZE_EXCEEDED, ex.getErrorCode());
	}

	@Test
	void validateSize_nullExtension_usesDocumentLimit() {
		assertDoesNotThrow(() -> service.validateSize(19 * 1024 * 1024, null)); // 19MB <
																				// 20MB
		assertThrows(BusinessException.class, () -> service.validateSize(21 * 1024 * 1024, null)); // 21MB
																									// >
																									// 20MB
	}

	// ── Magic bytes ──

	@Test
	void validateMagicBytes_jpegFile_matches() throws IOException {
		// Real JPEG magic bytes: FF D8 FF
		byte[] jpegBytes = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0 };
		MultipartFile file = mock(MultipartFile.class);
		when(file.getInputStream()).thenReturn(new ByteArrayInputStream(jpegBytes));
		when(file.getOriginalFilename()).thenReturn("photo.jpg");

		// Should not throw — JPEG magic bytes match .jpg extension
		assertDoesNotThrow(() -> service.validateMagicBytes(file, "jpg"));
	}

	@Test
	void validateMagicBytes_pngMaskedAsJpg_throws() throws IOException {
		// Real PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
		byte[] pngBytes = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
		MultipartFile file = mock(MultipartFile.class);
		when(file.getInputStream()).thenReturn(new ByteArrayInputStream(pngBytes));
		when(file.getOriginalFilename()).thenReturn("fake.jpg");

		BusinessException ex = assertThrows(BusinessException.class, () -> service.validateMagicBytes(file, "jpg"));
		assertEquals(ErrorCode.FILE_TYPE_MISMATCH, ex.getErrorCode());
	}

	// ── isImage ──

	@Test
	void isImage_jpg_true() {
		assertTrue(service.isImage("jpg"));
	}

	@Test
	void isImage_pdf_false() {
		assertFalse(service.isImage("pdf"));
	}

	@Test
	void isImage_null_false() {
		assertFalse(service.isImage(null));
	}

	// ── validate (整合) ──

	@Test
	void validate_emptyFile_throws() {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(true);

		assertThrows(BusinessException.class, () -> service.validate(file));
	}

	// ── N-5: YAML 屬性綁定 ──

	/**
	 * N-5：驗證 {@code file.validation.max-{image,document,media}-size} 三段 YAML 屬性 能正確注入到
	 * service 建構子（取代原本的硬編碼預設）。
	 */
	@Test
	void yamlBinding_customValues_overrideDefaults() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of())
			.withUserConfiguration(TestConfig.class)
			.withPropertyValues("file.validation.max-image-size=1048576", // 1MB
					"file.validation.max-document-size=2097152", // 2MB
					"file.validation.max-media-size=3145728" // 3MB
			)
			.run(ctx -> {
				FileValidationService svc = ctx.getBean(FileValidationService.class);
				assertThat(readLongField(svc, "maxImageSize")).isEqualTo(1048576L);
				assertThat(readLongField(svc, "maxDocumentSize")).isEqualTo(2097152L);
				assertThat(readLongField(svc, "maxMediaSize")).isEqualTo(3145728L);

				// 行為驗證：1MB 圖片上限下，2MB 圖片必須被拒
				BusinessException ex = assertThrows(BusinessException.class,
						() -> svc.validateSize(2 * 1024 * 1024L, "jpg"));
				assertEquals(ErrorCode.FILE_SIZE_EXCEEDED, ex.getErrorCode());
			});
	}

	/**
	 * N-5：未提供任何屬性時，套用 {@code @Value} 內預設值（5MB / 20MB / 100MB）。
	 */
	@Test
	void yamlBinding_noProperties_appliesDefaults() {
		new ApplicationContextRunner().withUserConfiguration(TestConfig.class).run(ctx -> {
			FileValidationService svc = ctx.getBean(FileValidationService.class);
			assertThat(readLongField(svc, "maxImageSize")).isEqualTo(5L * 1024 * 1024);
			assertThat(readLongField(svc, "maxDocumentSize")).isEqualTo(20L * 1024 * 1024);
			assertThat(readLongField(svc, "maxMediaSize")).isEqualTo(100L * 1024 * 1024);
		});
	}

	/**
	 * N-5：副檔名分派正確 — 同樣的位元組數，套用不同類別上限會有不同結果。
	 */
	@Test
	void yamlBinding_extensionDispatch_respectsCategoryLimits() {
		new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
			.withPropertyValues("file.validation.max-image-size=1024", // 1KB
					"file.validation.max-document-size=10240", // 10KB
					"file.validation.max-media-size=102400" // 100KB
			)
			.run(ctx -> {
				FileValidationService svc = ctx.getBean(FileValidationService.class);
				// 5KB：圖片拒、文件接受、影音接受
				assertThrows(BusinessException.class, () -> svc.validateSize(5120, "png"));
				assertDoesNotThrow(() -> svc.validateSize(5120, "pdf"));
				assertDoesNotThrow(() -> svc.validateSize(5120, "mp4"));
				// 50KB：圖片拒、文件拒、影音接受
				assertThrows(BusinessException.class, () -> svc.validateSize(51200, "jpg"));
				assertThrows(BusinessException.class, () -> svc.validateSize(51200, "xlsx"));
				assertDoesNotThrow(() -> svc.validateSize(51200, "mp3"));
			});
	}

	private static long readLongField(Object target, String name) throws Exception {
		Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.getLong(target);
	}

	@Configuration
	static class TestConfig {

		@Bean
		FileValidationService fileValidationService(
				@org.springframework.beans.factory.annotation.Value("${file.validation.max-image-size:5242880}") long maxImageSize,
				@org.springframework.beans.factory.annotation.Value("${file.validation.max-document-size:20971520}") long maxDocumentSize,
				@org.springframework.beans.factory.annotation.Value("${file.validation.max-media-size:104857600}") long maxMediaSize) {
			return new FileValidationService(maxImageSize, maxDocumentSize, maxMediaSize);
		}

	}

}
