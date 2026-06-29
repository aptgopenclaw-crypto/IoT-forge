package com.taipei.iot.audit.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditQueryRequest {

	@Size(max = 100)
	private String userName;

	@Size(max = 50)
	private String eventDesc;

	@Size(max = 30)
	private String startTimestamp;

	@Size(max = 30)
	private String endTimestamp;

	@Size(max = 50)
	private String sortBy;

	@Size(max = 10)
	private String sort;

}
