package com.taipei.iot.notification.channel;

import com.taipei.iot.notification.dto.NotificationPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NoOpSmsChannel implements NotificationChannel {

	@Override
	public String channelType() {
		return "SMS";
	}

	@Override
	public void send(NotificationPayload payload) {
		log.debug("NoOp SMS channel — no SMS gateway configured, skipping send for {} users",
				payload.getUserIds().size());
	}

}
