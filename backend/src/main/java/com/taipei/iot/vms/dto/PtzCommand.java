package com.taipei.iot.vms.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * PTZ 控制指令。
 *
 * @param direction 方向：LEFT / RIGHT / UP / DOWN / ZOOM_IN / ZOOM_OUT
 * @param speed 速度 1-100，預設 50
 * @param presetPoint 預設點（選用）
 */
public record PtzCommand(@NotBlank String direction, Integer speed, Integer presetPoint) {
}
