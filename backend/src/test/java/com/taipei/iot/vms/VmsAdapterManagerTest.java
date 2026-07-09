package com.taipei.iot.vms;

import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.vms.dto.CameraStreamInfo;
import com.taipei.iot.vms.dto.PtzCommand;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.enums.VmsType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link VmsAdapterManager} 註冊與調度行為。
 */
class VmsAdapterManagerTest {

	@Test
	@DisplayName("單一 adapter 註冊後可依 type 查詢")
	void getAdapter_happyPath() {
		var adapter = new StubAdapter(VmsType.NX_WITNESS);
		var manager = new VmsAdapterManager(List.of(adapter));

		var found = manager.getAdapter(VmsType.NX_WITNESS);
		assertThat(found).isSameAs(adapter);
	}

	@Test
	@DisplayName("多個 adapter 註冊後可分別查詢")
	void multipleAdapters_canBeFound() {
		var nx = new StubAdapter(VmsType.NX_WITNESS);
		var ms = new StubAdapter(VmsType.MILESTONE);
		var ax = new StubAdapter(VmsType.AXXON);
		var manager = new VmsAdapterManager(List.of(nx, ms, ax));

		assertThat(manager.getAdapter(VmsType.NX_WITNESS)).isSameAs(nx);
		assertThat(manager.getAdapter(VmsType.MILESTONE)).isSameAs(ms);
		assertThat(manager.getAdapter(VmsType.AXXON)).isSameAs(ax);
	}

	@Test
	@DisplayName("未知類型拋 BusinessException(VMS_TYPE_UNSUPPORTED)")
	void unknownType_throwsException() {
		var adapter = new StubAdapter(VmsType.NX_WITNESS);
		var manager = new VmsAdapterManager(List.of(adapter));

		assertThatThrownBy(() -> manager.getAdapter(VmsType.MILESTONE)).isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_TYPE_UNSUPPORTED);
	}

	@Test
	@DisplayName("空列表初始化後任何查詢都拋異常")
	void emptyList_anyQueryThrows() {
		var manager = new VmsAdapterManager(List.of());

		assertThatThrownBy(() -> manager.getAdapter(VmsType.NX_WITNESS)).isInstanceOf(BusinessException.class)
			.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VMS_TYPE_UNSUPPORTED);
	}

	@Test
	@DisplayName("allAdapters 回傳所有已註冊 adapter")
	void allAdapters_returnsAll() {
		var nx = new StubAdapter(VmsType.NX_WITNESS);
		var ms = new StubAdapter(VmsType.MILESTONE);
		var manager = new VmsAdapterManager(List.of(nx, ms));

		var all = manager.allAdapters();
		assertThat(all).hasSize(2).containsExactlyInAnyOrder(nx, ms);
	}

	/**
	 * 測試用 stub adapter，僅實作 getType()，其餘方法拋 UnsupportedOperationException。
	 */
	private static class StubAdapter implements VmsAdapter {

		private final VmsType type;

		StubAdapter(VmsType type) {
			this.type = type;
		}

		@Override
		public VmsType getType() {
			return type;
		}

		@Override
		public CameraStreamInfo getLiveStreamUrl(String cameraId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CameraStreamInfo getPlaybackUrl(String cameraId, Instant startTime, Instant endTime) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void controlPtz(String cameraId, PtzCommand command) {
			throw new UnsupportedOperationException();
		}

		@Override
		public VmsCamera getCameraInfo(String cameraId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<VmsCamera> listCameras(int page, int size) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean healthCheck() {
			throw new UnsupportedOperationException();
		}

	}

}
