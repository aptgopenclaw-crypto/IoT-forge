package com.taipei.iot.vms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.dto.VmsEvent;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.CameraStatus;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsEventType;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.repository.VmsCameraEventRepository;
import com.taipei.iot.vms.repository.VmsCameraRepository;
import com.taipei.iot.vms.repository.VmsServerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VmsEventService} webhook 處理行為。
 */
@ExtendWith(MockitoExtension.class)
class VmsEventServiceTest {

	@Mock
	private VmsCameraRepository vmsCameraRepository;

	@Mock
	private VmsCameraEventRepository vmsCameraEventRepository;

	@Mock
	private VmsServerRepository vmsServerRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private VmsEventService service;

	private final VmsServer testServer = VmsServer.builder()
		.id(1L)
		.tenantId("tenant-1")
		.name("Nx Server")
		.vmsType(VmsType.NX_WITNESS)
		.baseUrl("http://nx:7001")
		.authType(VmsAuthType.BASIC)
		.authUsername("admin")
		.authPassword("pass")
		.isActive(true)
		.build();

	private final VmsCamera testCamera = VmsCamera.builder()
		.id(1L)
		.tenantId("tenant-1")
		.server(testServer)
		.vmsCameraId("cam-001")
		.displayName("入口")
		.status(CameraStatus.ONLINE)
		.build();

	@BeforeEach
	void setUp() {
		service = new VmsEventService(vmsCameraRepository, vmsCameraEventRepository, vmsServerRepository,
				eventPublisher, objectMapper);
		TenantContext.setCurrentTenantId("tenant-1");
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@Test
	@DisplayName("正常 webhook → 寫入 DB + 發布事件")
	void processWebhook_happyPath() throws Exception {
		when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));
		when(vmsCameraRepository.findByTenantId("tenant-1")).thenReturn(List.of(testCamera));

		String payload = objectMapper.writeValueAsString(new VmsEvent("MOTION_DETECT", "cam-001",
				Instant.parse("2026-07-01T08:00:00Z"), Map.of("zone", "A1", "confidence", 0.95)));

		service.processWebhook(VmsType.NX_WITNESS, payload);

		// 驗證 entity 已儲存
		ArgumentCaptor<com.taipei.iot.vms.entity.VmsCameraEvent> entityCaptor = ArgumentCaptor
			.forClass(com.taipei.iot.vms.entity.VmsCameraEvent.class);
		verify(vmsCameraEventRepository).save(entityCaptor.capture());
		assertThat(entityCaptor.getValue().getEventType()).isEqualTo(VmsEventType.MOTION_DETECT);
		assertThat(entityCaptor.getValue().getTenantId()).isEqualTo("tenant-1");

		// 驗證 ApplicationEvent 已發布
		verify(eventPublisher).publishEvent(any(com.taipei.iot.vms.event.VmsCameraEvent.class));
	}

	@Test
	@DisplayName("camera 未映射 → 跳過處理（不拋異常）")
	void unmappedCamera_skips() throws Exception {
		when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));
		when(vmsCameraRepository.findByTenantId("tenant-1")).thenReturn(List.of()); // 無
																					// mapping

		String payload = objectMapper
			.writeValueAsString(new VmsEvent("MOTION_DETECT", "unknown-cam", Instant.now(), Map.of()));

		service.processWebhook(VmsType.NX_WITNESS, payload);

		verify(vmsCameraEventRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("缺少必要欄位 → 跳過處理")
	void missingRequiredFields_skips() throws Exception {
		String payload = objectMapper.writeValueAsString(Map.of("eventType", "MOTION_DETECT"));

		service.processWebhook(VmsType.NX_WITNESS, payload);

		verify(vmsCameraEventRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("無啟用 server → 跳過處理")
	void noActiveServer_skips() throws Exception {
		when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of());

		String payload = objectMapper
			.writeValueAsString(new VmsEvent("MOTION_DETECT", "cam-001", Instant.now(), Map.of()));

		service.processWebhook(VmsType.NX_WITNESS, payload);

		verify(vmsCameraEventRepository, never()).save(any());
		verify(eventPublisher, never()).publishEvent(any());
	}

	@Test
	@DisplayName("JSON 解析失敗 → 拋 VMS_EVENT_PROCESSING_FAILED")
	void invalidJson_throwsException() {
		assertThatThrownBy(() -> service.processWebhook(VmsType.NX_WITNESS, "not json"))
			.isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_EVENT_PROCESSING_FAILED);
	}

}
