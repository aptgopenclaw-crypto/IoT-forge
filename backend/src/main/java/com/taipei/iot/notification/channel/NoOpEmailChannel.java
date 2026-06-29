package com.taipei.iot.notification.channel;

import com.taipei.iot.notification.dto.NotificationPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("test")
public class NoOpEmailChannel implements NotificationChannel {

	@Override
	public String channelType() {
		return "EMAIL";
	}

	@Override
	public void send(NotificationPayload payload) {
		log.debug("NoOp email channel — skipping send for {} users", payload.getUserIds().size());
	}

}
