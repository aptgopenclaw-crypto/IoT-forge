package com.taipei.iot.vms.session;

public class SessionNotFoundException extends RuntimeException {

	public SessionNotFoundException(String sessionToken) {
		super("Session not found or expired: " + sessionToken);
	}

}
