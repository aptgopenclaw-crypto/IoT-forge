package com.taipei.iot.auth.policy;

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
class PasswordPolicyResolverTest {

	@Mock
	private PasswordPolicyDao dao;

	@InjectMocks
	private PasswordPolicyResolver resolver;

	@Test
	void resolve_nullTenant_returnsPlatformDefaults() {
		when(dao.findAllForTenant(PasswordPolicyResolver.PLATFORM_SENTINEL))
			.thenReturn(Map.of("password.min_length", "10", "password.require_uppercase", "false"));

		PasswordPolicy p = resolver.resolve(null);

		assertEquals(10, p.getMinLength());
		assertFalse(p.isRequireUppercase());
		// unspecified keys fall back to hard-coded defaults
		assertTrue(p.isRequireLowercase());
		assertEquals(5, p.getHistoryCount());
	}

	@Test
	void resolve_tenantOverrides_takePrecedenceOverPlatform() {
		when(dao.findAllForTenant("tenant-A"))
			.thenReturn(Map.of("password.min_length", "12", "password.require_special", "false"));
		when(dao.findAllForTenant(PasswordPolicyResolver.PLATFORM_SENTINEL))
			.thenReturn(Map.of("password.min_length", "8", "password.require_special", "true"));

		PasswordPolicy p = resolver.resolve("tenant-A");

		assertEquals(12, p.getMinLength());
		assertFalse(p.isRequireSpecial());
	}

	@Test
	void resolve_missingEverywhere_fallsBackToHardCoded() {
		when(dao.findAllForTenant(anyString())).thenReturn(Map.of());

		PasswordPolicy p = resolver.resolve("tenant-X");

		assertEquals(8, p.getMinLength());
		assertEquals(5, p.getHistoryCount());
		assertTrue(p.isRequireUppercase());
	}

	@Test
	void resolve_hitsDaoEveryCall_noInProcessCache() {
		when(dao.findAllForTenant("tenant-B")).thenReturn(Map.of("password.min_length", "9"));
		when(dao.findAllForTenant(PasswordPolicyResolver.PLATFORM_SENTINEL)).thenReturn(Map.of());

		resolver.resolve("tenant-B");
		resolver.resolve("tenant-B");
		resolver.resolve("tenant-B");

		// Cache was intentionally removed (see 04-current-inventory.md §3.2):
		// every resolve() must reach the DAO, eliminating the multi-instance
		// staleness window after a policy change.
		verify(dao, times(3)).findAllForTenant("tenant-B");
		verify(dao, times(3)).findAllForTenant(PasswordPolicyResolver.PLATFORM_SENTINEL);
	}

}
