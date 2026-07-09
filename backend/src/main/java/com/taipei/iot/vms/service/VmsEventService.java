package com.taipei.iot.vms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taipei.iot.common.context.TenantContext;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.vms.dto.VmsEvent;
import com.taipei.iot.vms.entity.VmsCamera;
import com.taipei.iot.vms.entity.VmsCameraEvent;
import com.taipei.iot.vms.entity.VmsServer;
import com.taipei.iot.vms.enums.VmsEventType;
import com.taipei.iot.vms.enums.VmsType;
import com.taipei.iot.vms.repository.VmsCameraEventRepository;
import com.taipei.iot.vms.repository.VmsCameraRepository;
import com.taipei.iot.vms.repository.VmsServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * VMS 事件處理服務。
 *
 * <p>
 * 接收 VMS webhook → 解析為標準 {@link VmsEvent} → 比對本地攝影機 → 寫入 DB → 發布 ApplicationEvent。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VmsEventService {

	private final VmsCameraRepository vmsCameraRepository;

	private final VmsCameraEventRepository vmsCameraEventRepository;

	private final VmsServerRepository vmsServerRepository;

	private final ApplicationEventPublisher eventPublisher;

	private final ObjectMapper objectMapper;

	/**
	 * 處理 VMS webhook 請求。
	 * @param vmsType VMS 類型
	 * @param rawPayload 原始 webhook 請求 body
	 */
	@Transactional
	public void processWebhook(VmsType vmsType, String rawPayload) {
		try {
			// 1. 解析 raw payload 為標準 VmsEvent
			VmsEvent vmsEvent = objectMapper.readValue(rawPayload, VmsEvent.class);
			if (vmsEvent.cameraId() == null || vmsEvent.eventType() == null) {
				log.warn("VMS webhook payload 缺少必要欄位: cameraId={}, eventType={}", vmsEvent.cameraId(),
						vmsEvent.eventType());
				return;
			}

			// 2. 查詢對應的 VMS server → 取得 tenantId
			List<VmsServer> servers = vmsServerRepository
				.findByTenantIdAndIsActiveTrue(TenantContext.getCurrentTenantId());
			if (servers.isEmpty()) {
				log.warn("VMS webhook 收到但無啟用 server (type={})", vmsType);
				return;
			}
			// 從 server 查找 camera（透過 vmsCameraId + vmsType 關聯）
			VmsServer server = servers.getFirst();
			Optional<VmsCamera> cameraOpt = vmsCameraRepository.findByTenantId(server.getTenantId())
				.stream()
				.filter(c -> c.getVmsCameraId().equals(vmsEvent.cameraId()))
				.findFirst();

			if (cameraOpt.isEmpty()) {
				log.warn("VMS webhook camera 未映射: vmsCameraId={}, vmsType={}", vmsEvent.cameraId(), vmsType);
				return;
			}
			VmsCamera camera = cameraOpt.get();

			// 3. 還原 TenantContext（確保寫入正確租戶）
			TenantContext.setCurrentTenantId(server.getTenantId());

			// 4. 寫入 vms_camera_events 表
			VmsEventType eventType = parseEventType(vmsEvent.eventType());
			String payloadJson = serializePayload(vmsEvent.payload());
			LocalDateTime occurredAt = vmsEvent.occurredAt() != null
					? LocalDateTime.ofInstant(vmsEvent.occurredAt(), ZoneOffset.UTC) : LocalDateTime.now();

			VmsCameraEvent eventEntity = VmsCameraEvent.builder()
				.tenantId(server.getTenantId())
				.camera(camera)
				.eventType(eventType)
				.payload(payloadJson != null ? parsePayloadMap(payloadJson) : null)
				.occurredAt(occurredAt)
				.build();
			vmsCameraEventRepository.save(eventEntity);

			// 5. 發布 ApplicationEvent
			var appEvent = new com.taipei.iot.vms.event.VmsCameraEvent(server.getTenantId(), camera.getId(),
					camera.getVmsCameraId(), eventType, payloadJson,
					vmsEvent.occurredAt() != null ? vmsEvent.occurredAt() : java.time.Instant.now());
			eventPublisher.publishEvent(appEvent);

			log.info("VMS 事件已處理: camera={}, eventType={}, occurredAt={}", camera.getDisplayName(), eventType,
					occurredAt);
		}
		catch (Exception ex) {
			log.error("VMS webhook 處理失敗: {}", ex.getMessage(), ex);
			throw new BusinessException(ErrorCode.VMS_EVENT_PROCESSING_FAILED, "VMS 事件處理失敗: " + ex.getMessage());
		}
	}

	private VmsEventType parseEventType(String rawType) {
		if (rawType == null) {
			return VmsEventType.UNKNOWN;
		}
		try {
			return VmsEventType.valueOf(rawType.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			log.debug("未知 VMS event type: {}, 以 UNKNOWN 代替", rawType);
			return VmsEventType.UNKNOWN;
		}
	}

	private String serializePayload(Map<String, Object> payload) {
		if (payload == null || payload.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(payload);
		}
		catch (JsonProcessingException e) {
			log.warn("序列化 payload 失敗: {}", e.getMessage());
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parsePayloadMap(String json) {
		try {
			return objectMapper.readValue(json, Map.class);
		}
		catch (JsonProcessingException e) {
			return null;
		}
	}

}
