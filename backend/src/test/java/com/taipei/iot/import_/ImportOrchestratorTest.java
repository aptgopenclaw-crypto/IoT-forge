package com.taipei.iot.import_;

import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.import_.config.ImportProperties;
import com.taipei.iot.import_.parser.FileParser;
import com.taipei.iot.import_.parser.FileParserFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportOrchestratorTest {

	@Mock
	private FileParserFactory fileParserFactory;

	@Mock
	private ImportProperties importProperties;

	@InjectMocks
	private ImportOrchestrator orchestrator;

	@Test
	void parseAndValidate_emptyFile_shouldThrow() {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(true);

		assertThrows(BusinessException.class, () -> orchestrator.parseAndValidate(file, null));
	}

	@Test
	void parseAndValidate_badExtension_shouldThrow() {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getOriginalFilename()).thenReturn("devices.txt");
		when(importProperties.getAllowedExtensions()).thenReturn("xlsx,csv");

		assertThrows(BusinessException.class, () -> orchestrator.parseAndValidate(file, null));
	}

	@Test
	@SuppressWarnings("unchecked")
	void parseAndValidate_exceedsMaxRows_shouldThrow() throws Exception {
		// Arrange
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getOriginalFilename()).thenReturn("devices.xlsx");
		when(file.getSize()).thenReturn(1024L);
		when(importProperties.getAllowedExtensions()).thenReturn("xlsx,csv");
		when(importProperties.getMaxFileSize()).thenReturn(10 * 1024 * 1024L);

		// Build a parser that returns more rows than maxRows
		FileParser parser = mock(FileParser.class);
		when(fileParserFactory.getParser("xlsx")).thenReturn(parser);

		List<Map<String, String>> manyRows = new ArrayList<>();
		for (int i = 0; i < 600; i++) {
			manyRows.add(Map.of("device_code", "D" + i));
		}
		InputStream fakeStream = new ByteArrayInputStream("fake".getBytes());
		when(file.getInputStream()).thenReturn(fakeStream);
		when(parser.parse(any(InputStream.class))).thenReturn(manyRows);

		when(importProperties.getMaxRows()).thenReturn(500);

		ImportStrategy<Object> strategy = mock(ImportStrategy.class);

		// Act & Assert
		assertThrows(BusinessException.class, () -> orchestrator.parseAndValidate(file, strategy));
	}

}
