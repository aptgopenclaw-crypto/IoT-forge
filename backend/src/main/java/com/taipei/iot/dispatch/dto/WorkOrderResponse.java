package com.taipei.iot.dispatch.dto;

import com.taipei.iot.dispatch.enums.WorkOrderSourceType;
import com.taipei.iot.dispatch.enums.WorkOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工單回應")
public class WorkOrderResponse {

	@Schema(description = "工單 ID")
	private Long id;

	@Schema(description = "關聯設備 ID")
	private Long deviceId;

	@Schema(description = "設備代碼")
	private String deviceCode;

	@Schema(description = "設備名稱")
	private String deviceName;

	@Schema(description = "關聯電力迴路 ID")
	private Long circuitId;

	@Schema(description = "工單類型")
	private String orderType;

	@Schema(description = "通報來源")
	private WorkOrderSourceType sourceType;

	@Schema(description = "工單狀態")
	private WorkOrderStatus status;

	@Schema(description = "優先級")
	private String priority;

	@Schema(description = "通報人姓名")
	private String reporterName;

	@Schema(description = "通報人聯絡方式")
	private String reporterContact;

	@Schema(description = "通報時間")
	private LocalDateTime reportedAt;

	@Schema(description = "問題描述")
	private String description;

	@Schema(description = "派工人員 ID")
	private String assignedTo;

	@Schema(description = "派工人員姓名")
	private String assignedToName;

	@Schema(description = "派工時間")
	private LocalDateTime assignedAt;

	@Schema(description = "到場時間")
	private LocalDateTime startedAt;

	@Schema(description = "完成時間")
	private LocalDateTime completedAt;

	@Schema(description = "完成備註")
	private String completionRemark;

	@Schema(description = "故障原因")
	private String faultCause;

	@Schema(description = "維修費用")
	private Integer repairCost;

	@Schema(description = "覆核人員 ID")
	private String reviewerId;

	@Schema(description = "覆核時間")
	private LocalDateTime reviewedAt;

	@Schema(description = "駁回原因")
	private String rejectReason;

	@Schema(description = "結案時間")
	private LocalDateTime closedAt;

	@Schema(description = "操作 Timeline")
	private List<WorkOrderLogEntry> timeline;

	@Schema(description = "建立者")
	private String createdBy;

	@Schema(description = "建立時間")
	private LocalDateTime createdAt;

	@Schema(description = "更新時間")
	private LocalDateTime updatedAt;

	@Getter
	@Setter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@Schema(description = "工單操作紀錄")
	public static class WorkOrderLogEntry {

		@Schema(description = "動作")
		private String action;

		@Schema(description = "操作人員")
		private String operatorName;

		@Schema(description = "GPS 緯度")
		private BigDecimal latitude;

		@Schema(description = "GPS 經度")
		private BigDecimal longitude;

		@Schema(description = "備註")
		private String note;

		@Schema(description = "操作時間")
		private LocalDateTime createdAt;

	}

}
