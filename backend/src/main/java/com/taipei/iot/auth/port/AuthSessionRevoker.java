package com.taipei.iot.auth.port;

import com.taipei.iot.auth.service.UserSessionService;
import com.taipei.iot.common.auth.port.SessionRevoker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link SessionRevoker} 的 auth 模組實作，委派給 {@link UserSessionService}。
 */
@Component
@RequiredArgsConstructor
public class AuthSessionRevoker implements SessionRevoker {

	private final UserSessionService userSessionService;

	@Override
	public void revokeAllExceptCurrent(String userId, String currentJti) {
		userSessionService.revokeAllExceptCurrent(userId, currentJti);
	}

}
