package com.taipei.iot.auth.service;

import com.taipei.iot.auth.dto.response.CaptchaResponse;

public interface CaptchaService {

	CaptchaResponse generate();

	boolean verify(String captchaKey, String captchaValue);

}
