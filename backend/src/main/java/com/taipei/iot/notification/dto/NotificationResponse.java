package com.taipei.iot.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.taipei.iot.notification.enums.NotificationRefType;
import com.taipei.iot.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationResponse {

	private Long id;

	private NotificationType type;

	private String title;

	private String content;

	private NotificationRefType refType;

	private String refId;

	private Boolean read;

	private LocalDateTime readAt;

	private LocalDateTime createdAt;

}
