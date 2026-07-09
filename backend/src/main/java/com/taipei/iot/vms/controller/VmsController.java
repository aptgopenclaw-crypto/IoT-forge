package com.taipei.iot.vms.controller;

import com.taipei.iot.common.dept.port.VisibleDeptScopeProvider;
import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.vms.dto.CameraLiveResponse;
import com.taipei.iot.vms.dto.CameraPlaybackResponse;
import com.taipei.iot.vms.dto.PtzCommand;
import com.taipei.iot.vms.dto.VmsCameraResponse;
import com.taipei.iot.vms.repository.VmsCameraRepository;
import com.taipei.iot.vms.service.VmsStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * VMS 即時影像 / 歷史回放 / PTZ 控制 API。
 *
 * <p>
 * 走既有 {@code /v1/auth/...} 鏈（JWT + scope），使用 {@code VMS_VIEW} / {@code VMS_MANAGE} 權限。
 * </p>
 */
@RestController
@RequestMapping("/v1/auth/vms")
@RequiredArgsConstructor
@Tag(name = "VMS", description = "影像監控 — 即時串流、歷史回放、PTZ 控制")
public class VmsController {

	private final VmsStreamService vmsStreamService;

	private final VmsCameraRepository vmsCameraRepository;

	private final VisibleDeptScopeProvider visibleDeptScopeProvider;

	@GetMapping("/cameras")
	@PreAuthorize("hasAuthority('VMS_VIEW')")
	@Operation(summary = "攝影機列表", description = "依資料權限 scope 列出當前使用者可見的攝影機")
	public BaseResponse<List<VmsCameraResponse>> listCameras() {
		String tenantId = TenantContext.getCurrentTenantId();
		List<Long> visibleDeptIds = visibleDeptScopeProvider.getVisibleDeptIds();

		List<VmsCameraResponse> cameras;
		if (visibleDeptIds.isEmpty()) {
			// ALL scope — 不限制部門
			cameras = vmsCameraRepository.findByTenantId(tenantId).stream().map(VmsCameraResponse::from).toList();
		}
		else {
			cameras = vmsCameraRepository.findByTenantIdAndDeptIdIn(tenantId, visibleDeptIds)
				.stream()
				.map(VmsCameraResponse::from)
				.toList();
		}
		return BaseResponse.success(cameras);
	}

	@GetMapping("/cameras/{id}/live")
	@PreAuthorize("hasAuthority('VMS_VIEW')")
	@Operation(summary = "即時影像", description = "取得攝影機即時串流播放 URL")
	public BaseResponse<CameraLiveResponse> getLiveStream(@PathVariable Long id) {
		return BaseResponse.success(vmsStreamService.getLiveStream(id));
	}

	@GetMapping("/cameras/{id}/playback")
	@PreAuthorize("hasAuthority('VMS_VIEW')")
	@Operation(summary = "歷史回放", description = "取得指定時間範圍的歷史回放串流 URL")
	public BaseResponse<CameraPlaybackResponse> getPlayback(@PathVariable Long id, @RequestParam Instant startTime,
			@RequestParam Instant endTime) {
		return BaseResponse.success(vmsStreamService.getPlayback(id, startTime, endTime));
	}

	@PostMapping("/cameras/{id}/ptz")
	@PreAuthorize("hasAuthority('VMS_MANAGE')")
	@Operation(summary = "PTZ 控制", description = "控制攝影機雲台方向、縮放")
	public BaseResponse<Void> controlPtz(@PathVariable Long id, @Valid @RequestBody PtzCommand command) {
		vmsStreamService.controlPtz(id, command);
		return BaseResponse.success(null);
	}

}
