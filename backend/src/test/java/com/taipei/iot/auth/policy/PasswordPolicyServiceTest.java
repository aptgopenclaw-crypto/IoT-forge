package com.taipei.iot.auth.policy;

import com.taipei.iot.auth.policy.dto.UpdatePasswordPolicyRequest;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordPolicyServiceTest {

	@Mock
	private PasswordPolicyResolver resolver;

	@Mock
	private PasswordPolicyDao dao;

	@InjectMocks
	private PasswordPolicyService service;

	private UpdatePasswordPolicyRequest req(String key, String value) {
		UpdatePasswordPolicyRequest r = new UpdatePasswordPolicyRequest();
		r.setKey(key);
		r.setValue(value);
		return r;
	}

	@BeforeEach
	void stubPlatform() {
		when(resolver.readRawPlatformDefaults())
			.thenReturn(Map.of("password.min_length", "8", "password.history_count", "5"));
	}

	@Test
	void updateTenantOverride_belowPlatformFloor_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateTenantOverride("tenant-A", req("password.min_length", "6")));
		assertEquals(ErrorCode.PASSWORD_POLICY_BELOW_PLATFORM_MINIMUM, ex.getErrorCode());
		verify(dao, never()).upsert(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void updateTenantOverride_atOrAbovePlatformFloor_persists() {
		service.updateTenantOverride("tenant-A", req("password.min_length", "10"));

		verify(dao).upsert(eq("tenant-A"), eq("password.min_length"), eq("10"), anyString());
	}

	@Test
	void updateTenantOverride_boolKey_skipsFloor() {
		service.updateTenantOverride("tenant-A", req("password.require_special", "false"));

		verify(dao).upsert(eq("tenant-A"), eq("password.require_special"), eq("false"), anyString());
	}

	@Test
	void updateTenantOverride_invalidKey_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateTenantOverride("tenant-A", req("password.something_unknown", "1")));
		assertEquals(ErrorCode.PASSWORD_POLICY_INVALID_KEY, ex.getErrorCode());
	}

	@Test
	void updateTenantOverride_invalidIntFormat_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateTenantOverride("tenant-A", req("password.min_length", "abc")));
		assertEquals(ErrorCode.PASSWORD_POLICY_INVALID_VALUE, ex.getErrorCode());
	}

	@Test
	void updateTenantOverride_invalidBoolFormat_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updateTenantOverride("tenant-A", req("password.require_digit", "yes")));
		assertEquals(ErrorCode.PASSWORD_POLICY_INVALID_VALUE, ex.getErrorCode());
	}

	@Test
	void updateTenantOverride_nullTenant_throws() {
		assertThrows(BusinessException.class,
				() -> service.updateTenantOverride(null, req("password.min_length", "10")));
	}

	@Test
	void updatePlatformDefault_belowKeyFloor_throws() {
		// min_length floor is 8 — platform itself must respect it.
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.updatePlatformDefault(req("password.min_length", "4")));
		assertEquals(ErrorCode.PASSWORD_POLICY_BELOW_PLATFORM_MINIMUM, ex.getErrorCode());
	}

	@Test
	void updatePlatformDefault_validValue_persists() {
		service.updatePlatformDefault(req("password.min_length", "12"));

		verify(dao).upsert(eq(PasswordPolicyResolver.PLATFORM_SENTINEL), eq("password.min_length"), eq("12"),
				anyString());
	}

	@Test
	void deleteTenantOverride_invokesDao() {
		service.deleteTenantOverride("tenant-A", "password.min_length");

		verify(dao).delete("tenant-A", "password.min_length");
	}

	@Test
	void deleteTenantOverride_invalidKey_throws() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.deleteTenantOverride("tenant-A", "password.bogus"));
		assertEquals(ErrorCode.PASSWORD_POLICY_INVALID_KEY, ex.getErrorCode());
	}

	@Test
	void getEffective_delegatesToResolver() {
		when(resolver.resolve("tenant-A")).thenReturn(PasswordPolicy.builder()
			.minLength(10)
			.requireUppercase(true)
			.requireLowercase(true)
			.requireDigit(true)
			.requireSpecial(true)
			.historyCount(3)
			.build());

		var dto = service.getEffective("tenant-A");
		assertEquals(10, dto.getMinLength());
		assertEquals(3, dto.getHistoryCount());
		assertFalse(dto.getDescribe().isEmpty());
	}

}
