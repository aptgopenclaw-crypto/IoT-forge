package com.taipei.iot.common.audit.port;

/**
 * 審計日誌中需要的使用者顯示資訊。
 *
 * <p>
 * 定義於 {@code common} 模組以作為 Port，避免 {@code audit} 直接依賴 {@code auth} 模組的 {@code UserEntity}
 * / {@code UserRepository}。
 *
 * @param displayName 使用者顯示名稱（可為 null）
 * @param email 電子郵件（可為 null）
 */
public record UserDisplayInfo(String displayName, String email) {
}
