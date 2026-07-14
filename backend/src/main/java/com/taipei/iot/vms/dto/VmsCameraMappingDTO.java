package com.taipei.iot.vms.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmsCameraMappingDTO {

	private Long id;

	private Long serverId;

	private String serverName;

	private String vmsCameraId;

	private String displayName;

	private Long deptId;

	private String deptName;

	private String status;

	private String rtspUrl;

	private LocalDateTime createdAt;

}
