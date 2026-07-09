package com.taipei.iot.vms.entity;

import com.taipei.iot.vms.enums.CameraStatus;
import com.taipei.iot.vms.enums.VmsType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VmsCamera} entity 映射與 builder 行為。
 */
class VmsCameraTest {

	@Test
	@DisplayName("builder 建立完整 entity")
	void builder_createsFullEntity() {
		var server = VmsServer.builder()
			.id(1L)
			.tenantId("tenant-1")
			.name("Server")
			.vmsType(VmsType.NX_WITNESS)
			.baseUrl("http://server")
			.build();

		var camera = VmsCamera.builder()
			.tenantId("tenant-1")
			.server(server)
			.vmsCameraId("cam-001")
			.displayName("入口攝影機")
			.deviceId(100L)
			.rtspUrl("rtsp://server:554/cam-001")
			.status(CameraStatus.ONLINE)
			.metadata(Map.of("resolution", "1920x1080", "fps", 30))
			.build();

		assertThat(camera.getTenantId()).isEqualTo("tenant-1");
		assertThat(camera.getServer()).isEqualTo(server);
		assertThat(camera.getVmsCameraId()).isEqualTo("cam-001");
		assertThat(camera.getDisplayName()).isEqualTo("入口攝影機");
		assertThat(camera.getDeviceId()).isEqualTo(100L);
		assertThat(camera.getRtspUrl()).isEqualTo("rtsp://server:554/cam-001");
		assertThat(camera.getStatus()).isEqualTo(CameraStatus.ONLINE);
		assertThat(camera.getMetadata()).containsEntry("resolution", "1920x1080").containsEntry("fps", 30);
	}

	@Test
	@DisplayName("預設值：status=ONLINE")
	void builder_defaultStatus() {
		var server = VmsServer.builder()
			.id(1L)
			.tenantId("t1")
			.name("S")
			.vmsType(VmsType.AXXON)
			.baseUrl("http://s")
			.build();

		var camera = VmsCamera.builder().tenantId("t1").server(server).vmsCameraId("cam-002").build();

		assertThat(camera.getStatus()).isEqualTo(CameraStatus.ONLINE);
	}

	@Test
	@DisplayName("TenantAware setter/getter")
	void tenantAware_works() {
		var camera = new VmsCamera();
		camera.setTenantId("t2");
		assertThat(camera.getTenantId()).isEqualTo("t2");
	}

}
