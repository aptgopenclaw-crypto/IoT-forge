package com.taipei.iot.audit.listener;

import com.taipei.iot.audit.async.AuditAsyncWriter;
import com.taipei.iot.audit.enums.AuditCategory;
import com.taipei.iot.audit.enums.AuditEventType;
import com.taipei.iot.common.event.VirusScanAuditEvent;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * F-3：驗證 {@link VirusScanAuditListener} 將事件正確翻譯成 {@link AuditAsyncWriter#saveAsync} 呼叫。
 */
@DisplayName("VirusScanAuditListener [common v2 F-3]")
@ExtendWith(MockitoExtension.class)
class VirusScanAuditListenerTest {

	@Mock
	AuditAsyncWriter auditAsyncWriter;

	@InjectMocks
	VirusScanAuditListener listener;

	private MockedStatic<SecurityContextUtils> securityCtx;

	@BeforeEach
	void setupContext() {
		TenantContext.setCurrentTenantId("tenant-A");
		securityCtx = Mockito.mockStatic(SecurityContextUtils.class);
		securityCtx.when(SecurityContextUtils::getCurrentUserId).thenReturn("user-1");
		securityCtx.when(SecurityContextUtils::getCurrentUsername).thenReturn("alice@example.com");
		securityCtx.when(SecurityContextUtils::getUserInfo).thenReturn(null);
	}

	@AfterEach
	void cleanup() {
		TenantContext.clear();
		securityCtx.close();
	}

	@Test
	@DisplayName("INFECTED 事件 → 寫入 FILE_VIRUS_INFECTED audit_log，含 tenantId/userId 與檔案資訊")
	void infectedEventTranslatesToFileVirusInfectedAuditEntry() {
		VirusScanAuditEvent event = new VirusScanAuditEvent(VirusScanAuditEvent.Result.INFECTED, "ann/9/uuid.pdf",
				"report.pdf", 5L, "ann/9");

		listener.onVirusScanAudit(event);

		verify(auditAsyncWriter, times(1)).saveAsync(eq("tenant-A"), eq("user-1"), eq("alice@example.com"),
				eq(AuditEventType.FILE_VIRUS_INFECTED.getValue()), eq(AuditCategory.SYSTEM.getValue()),
				Mockito.isNull(), // apiEndpoint（測試環境無 servlet）
				Mockito.contains("result=INFECTED"), // message 包含 result 與檔名
				eq(AuditEventType.FILE_VIRUS_INFECTED.errorCode()), // "99999"
				Mockito.isNull(), // ip
				Mockito.isNull(), // userAgent
				eq(0L), Mockito.isNull(), Mockito.isNull() // impersonatedBy
		);
	}

	@Test
	@DisplayName("ERROR 事件 → 寫入 FILE_SCAN_ERROR audit_log")
	void errorEventTranslatesToFileScanErrorAuditEntry() {
		VirusScanAuditEvent event = new VirusScanAuditEvent(VirusScanAuditEvent.Result.ERROR, "x/uuid.pdf",
				"report.pdf", 5L, "x");

		listener.onVirusScanAudit(event);

		verify(auditAsyncWriter, times(1)).saveAsync(eq("tenant-A"), eq("user-1"), eq("alice@example.com"),
				eq(AuditEventType.FILE_SCAN_ERROR.getValue()), eq(AuditCategory.SYSTEM.getValue()), Mockito.isNull(),
				Mockito.contains("result=ERROR"), eq(AuditEventType.FILE_SCAN_ERROR.errorCode()), Mockito.isNull(),
				Mockito.isNull(), eq(0L), Mockito.isNull(), Mockito.isNull());
	}

	@Test
	@DisplayName("auditAsyncWriter 拋例外時，listener 不應將例外傳播出去（best-effort）")
	void writerExceptionIsSwallowed() {
		VirusScanAuditEvent event = new VirusScanAuditEvent(VirusScanAuditEvent.Result.INFECTED, "x/u.pdf", "r.pdf", 1L,
				"x");
		Mockito.doThrow(new RuntimeException("db down"))
			.when(auditAsyncWriter)
			.saveAsync(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
					Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.any(),
					Mockito.any());

		// 不應拋出
		listener.onVirusScanAudit(event);

		verify(auditAsyncWriter, times(1)).saveAsync(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
				Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
				Mockito.anyLong(), Mockito.any(), Mockito.any());
	}

}
