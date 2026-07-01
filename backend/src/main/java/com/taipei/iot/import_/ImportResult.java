package com.taipei.iot.import_;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class ImportResult<T> {

	private final List<T> validRows;

	private final List<ImportError> errors;

	public boolean hasErrors() {
		return !errors.isEmpty();
	}

	public static <T> ImportResult<T> success(List<T> rows) {
		return new ImportResult<>(rows, Collections.emptyList());
	}

	public static <T> ImportResult<T> failure(List<ImportError> errors) {
		return new ImportResult<>(Collections.emptyList(), errors);
	}

}
