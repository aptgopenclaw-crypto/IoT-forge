package com.taipei.iot.auth.service.impl;

import com.taipei.iot.auth.dto.response.CaptchaResponse;
import com.taipei.iot.auth.service.CaptchaService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class NoOpCaptchaServiceImpl implements CaptchaService {

	@Override
	public CaptchaResponse generate() {
		return CaptchaResponse.builder().captchaKey("test-captcha-key").captchaImage("data:image/png;base64,").build();
	}

	@Override
	public boolean verify(String captchaKey, String captchaValue) {
		return true;
	}

}
