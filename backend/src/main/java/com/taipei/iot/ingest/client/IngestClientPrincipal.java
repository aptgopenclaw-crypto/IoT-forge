package com.taipei.iot.ingest.client;

/**
 * 通過 API key 認證後置入 Spring Security {@code Authentication} 的 principal，供 HTTP ingest
 * controller/service 取得租戶與憑證身分。
 *
 * @param clientId 憑證主鍵
 * @param tenantId 該憑證所屬租戶
 * @param clientName 憑證名稱（標示 source）
 */
public record IngestClientPrincipal(Long clientId, String tenantId, String clientName) {
}
