package com.taipei.iot.import_;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResponse {

	private String entityType;

	private int totalRows;

	private int successCount;

	private List<ImportError> errors;

}
