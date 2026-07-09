package com.taipei.iot.vms.entity;

import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VmsServer} entity 映射與 builder 行為。
 */
class VmsServerTest {

	@Test
	@DisplayName("builder 建立完整 entity")
	void builder_createsFullEntity() {
		var server = VmsServer.builder()
			.tenantId("tenant-1")
			.name("Nx Server Main")
			.vmsType(VmsType.NX_WITNESS)
			.baseUrl("http://nx-server:7001")
			.authType(VmsAuthType.BASIC)
			.authUsername("admin")
			.authPassword("encrypted-pass")
			.isActive(true)
			.build();

		assertThat(server.getTenantId()).isEqualTo("tenant-1");
		assertThat(server.getName()).isEqualTo("Nx Server Main");
		assertThat(server.getVmsType()).isEqualTo(VmsType.NX_WITNESS);
		assertThat(server.getBaseUrl()).isEqualTo("http://nx-server:7001");
		assertThat(server.getAuthType()).isEqualTo(VmsAuthType.BASIC);
		assertThat(server.getAuthUsername()).isEqualTo("admin");
		assertThat(server.getAuthPassword()).isEqualTo("encrypted-pass");
		assertThat(server.getIsActive()).isTrue();
	}

	@Test
	@DisplayName("預設值：authType=BASIC, isActive=true")
	void builder_defaultValues() {
		var server = VmsServer.builder()
			.tenantId("tenant-1")
			.name("Test")
			.vmsType(VmsType.MILESTONE)
			.baseUrl("http://test")
			.build();

		assertThat(server.getAuthType()).isEqualTo(VmsAuthType.BASIC);
		assertThat(server.getIsActive()).isTrue();
	}

	@Test
	@DisplayName("TenantAware setter/getter")
	void tenantAware_works() {
		var server = new VmsServer();
		server.setTenantId("tenant-x");
		assertThat(server.getTenantId()).isEqualTo("tenant-x");
	}

}
