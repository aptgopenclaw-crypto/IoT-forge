package com.taipei.iot.vms.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VmsServerRequest {

	private String name;

	private String vmsType;

	private String baseUrl;

	private String authType;

	private String authUsername;

	private String authPassword;

	private String apiToken;

	private Boolean isActive;

}
