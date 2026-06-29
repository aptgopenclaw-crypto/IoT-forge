package com.taipei.iot.notification.dto;

import com.taipei.iot.notification.enums.NotificationRefType;
import com.taipei.iot.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {

	private String tenantId;

	private List<String> userIds;

	private NotificationType type;

	private String title;

	private String content;

	private NotificationRefType refType;

	private String refId;

}
