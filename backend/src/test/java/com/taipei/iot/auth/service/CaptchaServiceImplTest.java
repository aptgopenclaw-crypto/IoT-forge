package com.taipei.iot.auth.service;

import com.taipei.iot.auth.service.impl.CaptchaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 針對 {@link CaptchaServiceImpl#verify(String, String)} 的單元測試， 確認 v2 N-1
 * 修復後的常數時間比對在各種輸入下行為正確。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaptchaServiceImplTest {

	@InjectMocks
	private CaptchaServiceImpl captchaService;

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOps;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(captchaService, "captchaTtl", 300);
		ReflectionTestUtils.setField(captchaService, "captchaLength", 4);
		ReflectionTestUtils.setField(captchaService, "skipVerification", false);
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
	}

	@Test
	void verify_exactMatch_returnsTrue() {
		when(valueOps.get("captcha:k1")).thenReturn("abcd");

		assertTrue(captchaService.verify("k1", "abcd"));
		verify(stringRedisTemplate).delete("captcha:k1");
	}

	@Test
	void verify_caseInsensitiveMatch_returnsTrue() {
		when(valueOps.get("captcha:k2")).thenReturn("aBcD");

		assertTrue(captchaService.verify("k2", "AbCd"));
		verify(stringRedisTemplate).delete("captcha:k2");
	}

	@Test
	void verify_wrongValue_returnsFalse() {
		when(valueOps.get("captcha:k3")).thenReturn("abcd");

		assertFalse(captchaService.verify("k3", "abce"));
		verify(stringRedisTemplate).delete("captcha:k3");
	}

	@Test
	void verify_lengthMismatch_returnsFalse() {
		when(valueOps.get("captcha:k4")).thenReturn("abcd");

		assertFalse(captchaService.verify("k4", "abcde"));
		verify(stringRedisTemplate).delete("captcha:k4");
	}

	@Test
	void verify_storedMissing_returnsFalse() {
		when(valueOps.get("captcha:k5")).thenReturn(null);

		assertFalse(captchaService.verify("k5", "abcd"));
		verify(stringRedisTemplate).delete("captcha:k5");
	}

	@Test
	void verify_nullInput_returnsFalse() {
		when(valueOps.get("captcha:k6")).thenReturn("abcd");

		assertFalse(captchaService.verify("k6", null));
		verify(stringRedisTemplate).delete("captcha:k6");
	}

	@Test
	void verify_skipVerification_returnsTrueWithoutRedis() {
		ReflectionTestUtils.setField(captchaService, "skipVerification", true);

		assertTrue(captchaService.verify("any", "any"));
		verify(stringRedisTemplate, never()).opsForValue();
		verify(stringRedisTemplate, never()).delete(anyString());
	}

}
