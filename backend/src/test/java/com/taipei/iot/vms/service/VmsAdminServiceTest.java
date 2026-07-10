package com.taipei.iot.vms.service;

import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.dept.port.VisibleDeptScopeProvider;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.VmsAdapter;
import com.taipei.iot.vms.VmsAdapterManager;
import com.taipei.iot.vms.dto.VmsCameraRequest;
import com.taipei.iot.vms.dto.VmsCameraResponse;
import com.taipei.iot.vms.dto.VmsServerRequest;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.CameraStatus;
import com.taipei.iot.vms.enums.VmsAuthType;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.repository.VmsCameraRepository;
import com.taipei.iot.vms.repository.VmsServerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VmsAdminService} CRUD 行為。
 */
@ExtendWith(MockitoExtension.class)
class VmsAdminServiceTest {

	@Mock
	private VmsServerRepository vmsServerRepository;

	@Mock
	private VmsCameraRepository vmsCameraRepository;

	@Mock
	private VmsAdapterManager vmsAdapterManager;

	@Mock
	private VmsAdapter vmsAdapter;

	@Mock
	private VisibleDeptScopeProvider visibleDeptScopeProvider;

	private VmsAdminService service;

	private final VmsServer testServer = VmsServer.builder()
		.id(1L)
		.tenantId("tenant-1")
		.name("Nx Server")
		.vmsType(VmsType.NX_WITNESS)
		.baseUrl("http://nx:7001")
		.authType(VmsAuthType.BASIC)
		.isActive(true)
		.build();

	@BeforeEach
	void setUp() {
		service = new VmsAdminService(vmsServerRepository, vmsCameraRepository, vmsAdapterManager,
				visibleDeptScopeProvider);
		TenantContext.setCurrentTenantId("tenant-1");
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@Nested
	@DisplayName("Servers")
	class Servers {

		@Test
		@DisplayName("listServers → 回傳 active server 列表")
		void listServers() {
			when(vmsServerRepository.findByTenantIdAndIsActiveTrue("tenant-1")).thenReturn(List.of(testServer));

			var result = service.listServers();
			assertThat(result).hasSize(1);
			assertThat(result.getFirst().name()).isEqualTo("Nx Server");
		}

		@Test
		@DisplayName("getServer → 存在回傳、不存在拋 VMS_SERVER_NOT_FOUND")
		void getServer() {
			when(vmsServerRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testServer));
			when(vmsServerRepository.findByIdAndTenantId(999L, "tenant-1")).thenReturn(Optional.empty());

			assertThat(service.getServer(1L).name()).isEqualTo("Nx Server");
			assertThatThrownBy(() -> service.getServer(999L)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_SERVER_NOT_FOUND);
		}

		@Test
		@DisplayName("createServer → 儲存並回傳含 id 的 response")
		void createServer() {
			var request = new VmsServerRequest("New Server", VmsType.NX_WITNESS, "http://new:7001", VmsAuthType.TOKEN,
					null, null, "tok-123");
			when(vmsServerRepository.save(any())).thenAnswer(inv -> {
				VmsServer s = inv.getArgument(0);
				// 模擬 id 生成
				try {
					var idField = VmsServer.class.getDeclaredField("id");
					idField.setAccessible(true);
					idField.set(s, 2L);
				}
				catch (Exception ignored) {
				}
				return s;
			});

			var result = service.createServer(request);

			assertThat(result.name()).isEqualTo("New Server");
			assertThat(result.vmsType()).isEqualTo(VmsType.NX_WITNESS);
		}

		@Test
		@DisplayName("deleteServer → 軟刪除 (isActive=false)")
		void deleteServer() {
			when(vmsServerRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testServer));

			service.deleteServer(1L);

			assertThat(testServer.getIsActive()).isFalse();
			verify(vmsServerRepository).save(testServer);
		}

		@Test
		@DisplayName("testConnection → success")
		void testConnection_success() {
			when(vmsServerRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testServer));
			when(vmsAdapterManager.getAdapter(VmsType.NX_WITNESS)).thenReturn(vmsAdapter);
			when(vmsAdapter.healthCheck()).thenReturn(true);

			var result = service.testConnection(1L);
			assertThat(result.name()).isEqualTo("Nx Server");
		}

		@Test
		@DisplayName("testConnection → 失敗拋 VMS_CONNECTION_FAILED")
		void testConnection_failed() {
			when(vmsServerRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testServer));
			when(vmsAdapterManager.getAdapter(VmsType.NX_WITNESS)).thenReturn(vmsAdapter);
			when(vmsAdapter.healthCheck()).thenReturn(false);

			assertThatThrownBy(() -> service.testConnection(1L)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_CONNECTION_FAILED);
		}

	}

	@Nested
	@DisplayName("Cameras")
	class Cameras {

		private final VmsCamera testCamera = VmsCamera.builder()
			.id(1L)
			.tenantId("tenant-1")
			.server(testServer)
			.vmsCameraId("cam-001")
			.displayName("入口")
			.status(CameraStatus.ONLINE)
			.build();

		@Test
		@DisplayName("listCameras → 依 serverId 篩選")
		void listCameras() {
			when(vmsCameraRepository.findByServerIdAndTenantId(1L, "tenant-1")).thenReturn(List.of(testCamera));
			when(vmsCameraRepository.findByTenantId("tenant-1")).thenReturn(List.of(testCamera));

			assertThat(service.listCameras(1L)).hasSize(1);
			assertThat(service.listCameras(null)).hasSize(1);
		}

		@Test
		@DisplayName("createCamera → 成功建立")
		void createCamera() {
			when(vmsServerRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testServer));
			when(vmsCameraRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			var request = new VmsCameraRequest(1L, "cam-002", "新攝影機", null, null, null);
			VmsCameraResponse result = service.createCamera(request);

			assertThat(result.displayName()).isEqualTo("新攝影機");
			assertThat(result.vmsCameraId()).isEqualTo("cam-002");
		}

		@Test
		@DisplayName("createCamera → server 不存在拋異常")
		void createCamera_serverNotFound() {
			when(vmsServerRepository.findByIdAndTenantId(999L, "tenant-1")).thenReturn(Optional.empty());

			var request = new VmsCameraRequest(999L, "cam-x", "x", null, null, null);
			assertThatThrownBy(() -> service.createCamera(request)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_SERVER_NOT_FOUND);
		}

		@Test
		@DisplayName("deleteCamera → 成功刪除")
		void deleteCamera() {
			when(vmsCameraRepository.findByIdAndTenantId(1L, "tenant-1")).thenReturn(Optional.of(testCamera));

			service.deleteCamera(1L);
			verify(vmsCameraRepository).delete(testCamera);
		}

		@Test
		@DisplayName("deleteCamera → 不存在拋異常")
		void deleteCamera_notFound() {
			when(vmsCameraRepository.findByIdAndTenantId(999L, "tenant-1")).thenReturn(Optional.empty());

			assertThatThrownBy(() -> service.deleteCamera(999L)).isInstanceOf(BusinessException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_CAMERA_NOT_FOUND);
		}

	}

}
