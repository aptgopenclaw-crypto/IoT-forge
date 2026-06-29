package com.taipei.iot.common.util;

import com.taipei.iot.common.enums.ErrorCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Build-time tool: generates a JSON mapping of ErrorCode enum → frontend i18n messages.
 * Invoked by exec-maven-plugin during the process-classes phase.
 */
public class ErrorCodeJsonGenerator {

	private ErrorCodeJsonGenerator() {
	}

	public static void main(String[] args) throws IOException {
		Path outputPath = args.length > 0 ? Path.of(args[0]) : Path.of("../frontend/src/generated/error-codes.json");
		String json = generate();
		Files.createDirectories(outputPath.getParent());
		Files.writeString(outputPath, json);
	}

	/**
	 * Generates a JSON string mapping error codes to their messages. Format: { "00000":
	 * "操作成功", "10001": "access token 無效", ... }
	 */
	public static String generate() {
		String entries = Arrays.stream(ErrorCode.values())
			.map(e -> "  \"%s\": \"%s\"".formatted(e.getCode(), escapeJson(e.getMessage())))
			.collect(Collectors.joining(",\n"));
		return "{\n" + entries + "\n}\n";
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}

}
