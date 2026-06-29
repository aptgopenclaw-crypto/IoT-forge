package com.taipei.iot.notification.channel;

import com.taipei.iot.notification.dto.NotificationPayload;

public interface NotificationChannel {

	String channelType();

	void send(NotificationPayload payload);

}
