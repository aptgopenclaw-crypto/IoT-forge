package com.taipei.iot.vms.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VmsCameraMappingRequest {

	private Long serverId;

	private String vmsCameraId;

	private String displayName;

	private Long deptId;

	private String rtspUrl;

}
