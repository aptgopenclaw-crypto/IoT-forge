package com.taipei.iot.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantEntityListenerTest {

	private final TenantEntityListener listener = new TenantEntityListener();

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	// ──────────────── PrePersist ────────────────

	@Test
	void prePersist_shouldSetTenantId_whenNull() {
		TenantContext.setCurrentTenantId("T1");

		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		listener.prePersist(entity);

		assertEquals("T1", entity.getTenantId());
	}

	@Test
	void prePersist_shouldNotOverride_whenAlreadySet() {
		TenantContext.setCurrentTenantId("T1");

		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		entity.setTenantId("T2");
		listener.prePersist(entity);

		assertEquals("T2", entity.getTenantId());
	}

	@Test
	void prePersist_systemContext_doesNotInjectMarker() {
		// SYSTEM context 下不應把 "SYSTEM" 寫進 entity；呼叫端需自行 setTenantId
		TenantContext.setSystemContext();

		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		listener.prePersist(entity);

		assertNull(entity.getTenantId());
	}

	// ──────────────── PreUpdate / PreRemove — happy path ────────────────

	@Test
	void preUpdate_sameTenant_passes() {
		TenantContext.setCurrentTenantId("T1");

		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		entity.setTenantId("T1");

		assertDoesNotThrow(() -> listener.preUpdate(entity));
	}

	@Test
	void preUpdate_systemContext_passes_evenForOtherTenant() {
		TenantContext.setSystemContext();

		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		entity.setTenantId("T2");

		assertDoesNotThrow(() -> listener.preUpdate(entity));
	}

	@Test
	void preUpdate_nonTenantAware_passes() {
		// 非 TenantAware 的 entity（例如 audit log 中的非租戶資料）即使 context null 也應放行
		assertDoesNotThrow(() -> listener.preUpdate(new Object()));
	}

	// ──────────────── PreUpdate / PreRemove — cross-tenant block ────────────────

	@Test
	void preUpdate_crossTenant_throwsSecurityException() {
		TenantContext.setCurrentTenantId("T1");

		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		entity.setTenantId("T2");

		SecurityException ex = assertThrows(SecurityException.class, () -> listener.preUpdate(entity));
		assertTrue(ex.getMessage().contains("Tenant mismatch"));
		assertTrue(ex.getMessage().contains("UPDATE"));
	}

	@Test
	void preRemove_crossTenant_throwsSecurityException() {
		TenantContext.setCurrentTenantId("T1");

		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		entity.setTenantId("T2");

		SecurityException ex = assertThrows(SecurityException.class, () -> listener.preRemove(entity));
		assertTrue(ex.getMessage().contains("Tenant mismatch"));
		assertTrue(ex.getMessage().contains("DELETE"));
	}

	// ──────────────── [T-6] fail-closed when TenantContext is null ────────────────

	@Test
	void preUpdate_nullContext_failClosed() {
		// 模擬「非 HTTP 路徑、未顯式 setSystemContext()」誤觸發 update
		assertNull(TenantContext.getCurrentTenantId());

		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		entity.setTenantId("T1");

		SecurityException ex = assertThrows(SecurityException.class, () -> listener.preUpdate(entity));
		assertTrue(ex.getMessage().contains("TenantContext is not set"));
		assertTrue(ex.getMessage().contains("UPDATE"));
		assertTrue(ex.getMessage().contains("setSystemContext"),
				"error message should hint at the legitimate cross-tenant API");
	}

	@Test
	void preRemove_nullContext_failClosed() {
		assertNull(TenantContext.getCurrentTenantId());

		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		entity.setTenantId("T1");

		SecurityException ex = assertThrows(SecurityException.class, () -> listener.preRemove(entity));
		assertTrue(ex.getMessage().contains("TenantContext is not set"));
		assertTrue(ex.getMessage().contains("DELETE"));
	}

	@Test
	void preUpdate_nullContext_nonTenantAware_stillPasses() {
		// 非 TenantAware 物件不在 T-6 守備範圍內
		assertNull(TenantContext.getCurrentTenantId());
		assertDoesNotThrow(() -> listener.preUpdate(new Object()));
	}

	@Test
	void preUpdate_runInSystemContext_bypassesFailClosed() {
		// 推薦的 escape hatch：runInSystemContext() 顯式宣告跨租戶操作
		TestTenantAwareEntity entity = new TestTenantAwareEntity();
		entity.setTenantId("T9");

		TenantContext.runInSystemContext(() -> listener.preUpdate(entity));
		// 執行完應恢復原 context（此處原為 null）
		assertNull(TenantContext.getCurrentTenantId());
	}

}
