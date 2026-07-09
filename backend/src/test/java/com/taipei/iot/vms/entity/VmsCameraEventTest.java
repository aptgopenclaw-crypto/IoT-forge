package com.taipei.iot.vms.entity;

import com.taipei.iot.vms.enums.VmsEventType;
import com.taipei.iot.vms.enums.VmsType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VmsCameraEvent} entity 映射與 builder 行為。
 */
class VmsCameraEventTest {

	@Test
	@DisplayName("builder 建立完整 entity")
	void builder_createsFullEntity() {
		var server = VmsServer.builder()
			.id(1L)
			.tenantId("t1")
			.name("S")
			.vmsType(VmsType.NX_WITNESS)
			.baseUrl("http://s")
			.build();
		var camera = VmsCamera.builder().id(10L).tenantId("t1").server(server).vmsCameraId("cam-001").build();

		var now = LocalDateTime.now();
		var event = VmsCameraEvent.builder()
			.tenantId("t1")
			.camera(camera)
			.eventType(VmsEventType.MOTION_DETECT)
			.payload(Map.of("zone", "A1", "confidence", 0.95))
			.occurredAt(now)
			.build();

		assertThat(event.getTenantId()).isEqualTo("t1");
		assertThat(event.getCamera()).isEqualTo(camera);
		assertThat(event.getEventType()).isEqualTo(VmsEventType.MOTION_DETECT);
		assertThat(event.getPayload()).containsEntry("zone", "A1").containsEntry("confidence", 0.95);
		assertThat(event.getOccurredAt()).isEqualTo(now);
	}

	@Test
	@DisplayName("TenantAware setter/getter")
	void tenantAware_works() {
		var event = new VmsCameraEvent();
		event.setTenantId("t2");
		assertThat(event.getTenantId()).isEqualTo("t2");
	}

}
