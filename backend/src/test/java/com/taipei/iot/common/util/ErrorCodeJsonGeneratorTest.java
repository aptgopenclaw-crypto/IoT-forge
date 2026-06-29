package com.taipei.iot.common.util;

import com.taipei.iot.common.enums.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeJsonGeneratorTest {

	@Test
	void generate_shouldProduceValidJson() {
		String json = ErrorCodeJsonGenerator.generate();

		assertThat(json).startsWith("{\n");
		assertThat(json).endsWith("}\n");
	}

	@Test
	void generate_shouldContainAllErrorCodes() {
		String json = ErrorCodeJsonGenerator.generate();

		Arrays.stream(ErrorCode.values()).forEach(ec -> assertThat(json).contains("\"" + ec.getCode() + "\""));
	}

	@Test
	void generate_shouldContainAllMessages() {
		String json = ErrorCodeJsonGenerator.generate();

		Arrays.stream(ErrorCode.values()).forEach(ec -> assertThat(json).contains("\"" + ec.getMessage() + "\""));
	}

	@Test
	void generate_shouldHaveCorrectEntryCount() {
		String json = ErrorCodeJsonGenerator.generate();

		long entryCount = json.lines().filter(line -> line.trim().startsWith("\"")).count();
		assertThat(entryCount).isEqualTo(ErrorCode.values().length);
	}

	@Test
	void main_shouldWriteFileToSpecifiedPath(@TempDir Path tempDir) throws IOException {
		Path outputPath = tempDir.resolve("error-codes.json");

		ErrorCodeJsonGenerator.main(new String[] { outputPath.toString() });

		assertThat(outputPath).exists();
		String content = Files.readString(outputPath);
		assertThat(content).isEqualTo(ErrorCodeJsonGenerator.generate());
	}

	@Test
	void main_shouldCreateParentDirectories(@TempDir Path tempDir) throws IOException {
		Path outputPath = tempDir.resolve("sub/dir/error-codes.json");

		ErrorCodeJsonGenerator.main(new String[] { outputPath.toString() });

		assertThat(outputPath).exists();
	}

	@Test
	void generate_shouldEscapeSpecialCharacters() {
		// Verify the output is parseable — no unescaped quotes or backslashes
		String json = ErrorCodeJsonGenerator.generate();

		// Each line with a key-value should have balanced quotes
		json.lines().filter(line -> line.trim().startsWith("\"")).forEach(line -> {
			// Remove trailing comma if present
			String trimmed = line.trim();
			if (trimmed.endsWith(",")) {
				trimmed = trimmed.substring(0, trimmed.length() - 1);
			}
			// Should be in format "code": "message"
			assertThat(trimmed).matches("\"[^\"]+\": \"[^\"]*\"");
		});
	}

	@Test
	void generatedFile_shouldBeInSyncWithEnum(@TempDir Path tempDir) throws IOException {
		// Simulate what exec-maven-plugin does
		Path outputPath = tempDir.resolve("error-codes.json");
		ErrorCodeJsonGenerator.main(new String[] { outputPath.toString() });

		String content = Files.readString(outputPath);

		// Verify SUCCESS code entry
		assertThat(content).contains("\"00000\": \"操作成功\"");
		// Verify UNKNOWN_ERROR entry (last)
		assertThat(content).contains("\"99999\": \"未知錯誤\"");
	}

}
