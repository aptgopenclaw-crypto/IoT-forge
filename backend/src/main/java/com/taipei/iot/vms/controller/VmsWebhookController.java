package com.taipei.iot.vms.controller;

import com.taipei.iot.common.response.BaseResponse;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.service.VmsEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * VMS Webhook 接收端點。
 *
 * <p>
 * 接收各 VMS 系統主動推播的事件（移動偵測、鏡頭離線等）。 此端點不經 JWT 驗證，以 IP whitelist 保護（見 Step 8）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/v1/vms/webhook")
@RequiredArgsConstructor
public class VmsWebhookController {

	private final VmsEventService vmsEventService;

	@PostMapping(value = "/{vmsType}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public BaseResponse<Void> receiveWebhook(@PathVariable String vmsType, @RequestBody String rawPayload) {
		VmsType type = parseVmsType(vmsType);
		if (type == null) {
			log.warn("不支援的 VMS webhook type: {}", vmsType);
			return BaseResponse.success(null);
		}
		vmsEventService.processWebhook(type, rawPayload);
		return BaseResponse.success(null);
	}

	private VmsType parseVmsType(String vmsType) {
		try {
			return VmsType.valueOf(vmsType.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

}
