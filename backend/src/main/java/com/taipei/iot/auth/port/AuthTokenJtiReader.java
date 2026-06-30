package com.taipei.iot.auth.port;

import com.taipei.iot.auth.security.JwtUtil;
import com.taipei.iot.common.auth.port.TokenJtiReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TokenJtiReader} 的 auth 模組實作，委派給 {@link JwtUtil}。
 */
@Component
@RequiredArgsConstructor
public class AuthTokenJtiReader implements TokenJtiReader {

	private final JwtUtil jwtUtil;

	@Override
	public String extractJti(String token) {
		return jwtUtil.parseToken(token).getId();
	}

}
