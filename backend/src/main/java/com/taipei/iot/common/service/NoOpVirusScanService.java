package com.taipei.iot.common.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * NoOp 病毒掃描 — 僅限 dev / test 環境使用，所有檔案直接回傳 CLEAN。
 * <p>
 * <b>N-2 修補 (2026-05-27)</b>：
 * </p>
 * <ul>
 * <li>移除 {@code matchIfMissing = true}：若設定遺漏，Spring 將無法注入 VirusScanService bean，
 * 啟動即失敗（fail-fast），避免「prod 忘記設定即靜默放行所有檔案」的安全陷阱。</li>
 * <li>加上 {@code @Profile("dev", "test")}：即使 {@code virus-scan.enabled=false} 被誤設於 prod， 此
 * bean 也不會被載入。</li>
 * <li>{@link #warnOnStartup()} 啟動時記 WARN，於 dev / test 環境提醒「此服務不會真正掃毒」。</li>
 * </ul>
 *
 * @see ClamAvVirusScanService 正式環境請使用此實作（{@code virus-scan.enabled=true}）
 */
@Slf4j
@Service
@Profile({ "dev", "test" })
@ConditionalOnProperty(name = "virus-scan.enabled", havingValue = "false")
public class NoOpVirusScanService implements VirusScanService {

	@PostConstruct
	void warnOnStartup() {
		log.warn("Virus scan is DISABLED (NoOpVirusScanService active). "
				+ "All uploaded files will be marked CLEAN without inspection. "
				+ "This MUST be enabled in production by setting virus-scan.enabled=true.");
	}

	@Override
	public ScanResult scan(String filePath) {
		log.debug("NoOp virus scan — skipping: {}", filePath);
		return ScanResult.CLEAN;
	}

}
