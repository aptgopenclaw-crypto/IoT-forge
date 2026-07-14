package com.taipei.iot.vms.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmsServerDTO {

	private Long id;

	private String name;

	private String vmsType;

	private String baseUrl;

	private String authType;

	private String authUsername;

	// authPassword intentionally omitted from response
	private String apiToken;

	private Boolean isActive;

	private LocalDateTime createdAt;

}
