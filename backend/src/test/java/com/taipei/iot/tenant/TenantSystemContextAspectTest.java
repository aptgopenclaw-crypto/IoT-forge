package com.taipei.iot.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [Tenant v2 T-13] 驗證 {@link TenantSystemContextAspect}： 標註
 * {@link RunInSystemTenantContext} 的方法執行期間 context 為 SYSTEM， 結束後（無論成功、執行期例外、checked
 * 例外）皆會恢復先前 context。
 */
class TenantSystemContextAspectTest {

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	/** 受測 bean：用 AspectJ proxy 套上 aspect，模擬 Spring container 行為。 */
	static class TargetBean {

		@RunInSystemTenantContext
		public String captureContext(AtomicReference<String> insideRef) {
			insideRef.set(TenantContext.getCurrentTenantId());
			return "ok";
		}

		@RunInSystemTenantContext
		public void throwRuntime() {
			throw new IllegalStateException("boom");
		}

		@RunInSystemTenantContext
		public void throwChecked() throws IOException {
			throw new IOException("checked boom");
		}

	}

	private TargetBean proxy() {
		AspectJProxyFactory factory = new AspectJProxyFactory(new TargetBean());
		factory.addAspect(new TenantSystemContextAspect());
		return factory.getProxy();
	}

	@Test
	void annotatedMethod_runsInSystemContext_andRestoresPrevious() {
		TenantContext.setCurrentTenantId("tenant-A");
		AtomicReference<String> inside = new AtomicReference<>();

		String result = proxy().captureContext(inside);

		assertEquals("ok", result);
		assertEquals("SYSTEM", inside.get(), "method body must execute in SYSTEM context");
		assertEquals("tenant-A", TenantContext.getCurrentTenantId(),
				"previous tenant context must be restored after method returns");
	}

	@Test
	void annotatedMethod_withNoPreviousContext_clearsAfterReturn() {
		assertNull(TenantContext.getCurrentTenantId());
		AtomicReference<String> inside = new AtomicReference<>();

		proxy().captureContext(inside);

		assertEquals("SYSTEM", inside.get());
		assertNull(TenantContext.getCurrentTenantId(),
				"no previous context → ThreadLocal must be cleared, not left as SYSTEM");
	}

	@Test
	void runtimeException_propagatesAndRestoresContext() {
		TenantContext.setCurrentTenantId("tenant-B");

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> proxy().throwRuntime());

		assertEquals("boom", ex.getMessage());
		assertEquals("tenant-B", TenantContext.getCurrentTenantId(),
				"context must be restored even when method throws");
	}

	@Test
	void checkedException_unwrapped_andRestoresContext() {
		TenantContext.setCurrentTenantId("tenant-C");

		IOException ex = assertThrows(IOException.class, () -> proxy().throwChecked());

		assertEquals("checked boom", ex.getMessage(),
				"aspect must unwrap CheckedAroundException and rethrow the original checked exception");
		assertEquals("tenant-C", TenantContext.getCurrentTenantId());
	}

}
