package com.taipei.iot.auth.service;

import com.taipei.iot.auth.dto.response.CaptchaResponse;
import com.taipei.iot.auth.service.impl.NoOpCaptchaServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaptchaServiceTest {

	private final CaptchaService captchaService = new NoOpCaptchaServiceImpl();

	@Test
	void generate_shouldReturnCaptchaResponse() {
		CaptchaResponse response = captchaService.generate();
		assertNotNull(response);
		assertNotNull(response.getCaptchaKey());
		assertNotNull(response.getCaptchaImage());
	}

	@Test
	void verify_shouldReturnTrue() {
		assertTrue(captchaService.verify("any-key", "any-value"));
	}

	@Test
	void verify_wrongInput_shouldStillReturnTrue_noOpImpl() {
		assertTrue(captchaService.verify("key", "wrong"));
	}

	@Test
	void verify_expired_shouldStillReturnTrue_noOpImpl() {
		assertTrue(captchaService.verify("expired-key", "1234"));
	}

	@Test
	void verify_caseInsensitive_shouldReturnTrue_noOpImpl() {
		assertTrue(captchaService.verify("key", "AbCd"));
	}

	@Test
	void verify_skipVerification_shouldReturnTrue_noOpImpl() {
		assertTrue(captchaService.verify("key", "value"));
	}

}
