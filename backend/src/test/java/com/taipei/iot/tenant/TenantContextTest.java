package com.taipei.iot.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	@Test
	void setAndGet_shouldReturnSameValue() {
		TenantContext.setCurrentTenantId("T1");
		assertEquals("T1", TenantContext.getCurrentTenantId());
	}

	@Test
	void clear_shouldRemoveValue() {
		TenantContext.setCurrentTenantId("T1");
		TenantContext.clear();
		assertNull(TenantContext.getCurrentTenantId());
	}

	@Test
	void setSystemContext_shouldReturnTrue() {
		TenantContext.setSystemContext();
		assertTrue(TenantContext.isSystemContext());
	}

	// ─── [Phase B] Impersonator ─────────────────────────────────────────

	@Test
	void impersonator_setAndGet_shouldReturnSameValue() {
		TenantContext.setImpersonator("super-admin-001");
		assertEquals("super-admin-001", TenantContext.getImpersonator());
		assertTrue(TenantContext.isImpersonating());
	}

	@Test
	void clear_shouldRemoveBothTenantAndImpersonator() {
		TenantContext.setCurrentTenantId("T1");
		TenantContext.setImpersonator("super-admin-001");
		TenantContext.clear();
		assertNull(TenantContext.getCurrentTenantId());
		assertNull(TenantContext.getImpersonator());
		assertFalse(TenantContext.isImpersonating());
	}

	@Test
	void runInSystemContext_shouldSaveAndRestoreBothTenantAndImpersonator() {
		TenantContext.setCurrentTenantId("T1");
		TenantContext.setImpersonator("super-admin-001");

		String result = TenantContext.runInSystemContext(() -> {
			assertTrue(TenantContext.isSystemContext(), "should switch to SYSTEM");
			assertNull(TenantContext.getImpersonator(), "impersonator should be cleared inside SYSTEM block");
			return "ok";
		});

		assertEquals("ok", result);
		assertEquals("T1", TenantContext.getCurrentTenantId(), "tenant restored");
		assertEquals("super-admin-001", TenantContext.getImpersonator(), "impersonator restored");
	}

	@Test
	void runInSystemContext_shouldRestoreEvenOnException() {
		TenantContext.setCurrentTenantId("T1");
		TenantContext.setImpersonator("super-admin-001");

		assertThrows(IllegalStateException.class, () -> TenantContext.runInSystemContext(() -> {
			throw new IllegalStateException("boom");
		}));

		assertEquals("T1", TenantContext.getCurrentTenantId());
		assertEquals("super-admin-001", TenantContext.getImpersonator());
	}

	@Test
	void runInSystemContext_nested_shouldRestoreProperly() {
		TenantContext.setCurrentTenantId("T1");
		TenantContext.setImpersonator("imp-1");

		TenantContext.runInSystemContext(() -> {
			// outer SYSTEM
			assertTrue(TenantContext.isSystemContext());
			assertNull(TenantContext.getImpersonator());
			TenantContext.runInSystemContext(() -> {
				assertTrue(TenantContext.isSystemContext());
				return null;
			});
			// still SYSTEM after inner returns
			assertTrue(TenantContext.isSystemContext());
			return null;
		});

		assertEquals("T1", TenantContext.getCurrentTenantId());
		assertEquals("imp-1", TenantContext.getImpersonator());
	}

	// ─── [Phase B] Trusted system context ───────────────────────────────

	@Test
	void trustedSystemContext_shouldBeTrueInsideRunInSystemContext() {
		assertFalse(TenantContext.isTrustedSystemContext());
		TenantContext.runInSystemContext(() -> {
			assertTrue(TenantContext.isTrustedSystemContext());
			return null;
		});
		assertFalse(TenantContext.isTrustedSystemContext());
	}

	@Test
	void trustedSystemContext_shouldBeFalseForDirectSetSystemContext() {
		TenantContext.setSystemContext();
		assertFalse(TenantContext.isTrustedSystemContext());
	}

	@Test
	void clear_shouldRemoveTrustedFlag() {
		TenantContext.runInSystemContext(() -> {
			assertTrue(TenantContext.isTrustedSystemContext());
			TenantContext.clear();
			assertFalse(TenantContext.isTrustedSystemContext());
			return null;
		});
	}

}
